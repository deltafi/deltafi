/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
// ABOUTME: Artifact registry client for publishing and retrieving artifacts via core's REST API.
// ABOUTME: Supports plugin-scoped and shared artifacts, caching, and version change callbacks via Valkey pub/sub.

package actionkit

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"net/http"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/sync/singleflight"
)

const (
	artifactsPath         = "/api/v2/artifacts"
	sharedOwner           = "shared"
	activeAlias           = "active"
	versionChangedChannel = "deltafi:artifact:version-changed"
	pluginPrefix          = "plugins/"
)

const (
	defaultArtifactCacheTTL        = 10 * time.Second
	defaultArtifactFailureCacheTTL = 1 * time.Second
)

// VersionChangedEvent describes an artifact version change received via Valkey pub/sub.
type VersionChangedEvent struct {
	Prefix          string  `json:"prefix"`
	ArtifactName    string  `json:"artifactName"`
	Alias           string  `json:"alias"`
	PreviousVersion string  `json:"previousVersion"`
	NewVersion      string  `json:"newVersion"`
	Timestamp       float64 `json:"timestamp"` // epoch seconds with fractional nanos
}

// IsShared returns true if the event is for the shared namespace.
func (e VersionChangedEvent) IsShared() bool {
	return e.Prefix == sharedOwner
}

// Owner extracts the owner from the event prefix.
// For shared artifacts, returns "shared". For plugin artifacts, strips the "plugins/" prefix.
func (e VersionChangedEvent) Owner() string {
	if e.IsShared() {
		return sharedOwner
	}
	return strings.TrimPrefix(e.Prefix, pluginPrefix)
}

type artifactCallback struct {
	owner        string
	artifactName string
	alias        string
	handler      func(VersionChangedEvent)
}

// ArtifactClientBase is the interface for artifact operations from actions.
// ArtifactClient (HTTP) and MockArtifactClient (testing) both implement this.
type ArtifactClientBase interface {
	PublishArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error)
	PublishSharedArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error)
	GetArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error)
	GetSharedArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error)
	StreamArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error)
	StreamSharedArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error)
}

// ArtifactClient publishes and retrieves artifacts via core's artifact registry API.
// Get results are cached; Stream results bypass the cache.
//
// cacheTTL and failureCacheTTL are stored as int64 nanoseconds via atomic ops
// so SetCacheTTL is safe to call from any goroutine. The cache map itself is
// protected by mu. The singleflight group collapses concurrent first-time
// fetches for the same cache key into a single underlying request.
type ArtifactClient struct {
	coreURL         string
	owner           string
	httpClient      *http.Client
	cacheTTL        atomic.Int64 // nanoseconds
	failureCacheTTL atomic.Int64 // nanoseconds
	mu              sync.RWMutex
	cache           map[string]*artifactCacheEntry
	flight          singleflight.Group
	callbackMu      sync.Mutex
	callbacks       []artifactCallback
	startupFiredIdx int
}

type artifactCacheEntry struct {
	artifact  *FetchedArtifact
	err       error
	expiresAt time.Time
}

// NewArtifactClient creates a client for artifact registry operations.
// Owner is the plugin identifier (typically "groupId.artifactId").
// Cache TTL defaults to 10s, overridable via ARTIFACT_CACHE_TTL env var.
// Failure cache TTL defaults to 1s, overridable via ARTIFACT_FAILURE_CACHE_TTL env var.
func NewArtifactClient(coreURL string, owner string) *ArtifactClient {
	c := &ArtifactClient{
		coreURL:    coreURL,
		owner:      owner,
		httpClient: &http.Client{Timeout: 5 * time.Minute},
		cache:      make(map[string]*artifactCacheEntry),
	}
	c.cacheTTL.Store(int64(parseCacheTTL(os.Getenv("ARTIFACT_CACHE_TTL"), defaultArtifactCacheTTL)))
	c.failureCacheTTL.Store(int64(parseCacheTTL(os.Getenv("ARTIFACT_FAILURE_CACHE_TTL"), defaultArtifactFailureCacheTTL)))
	return c
}

// SetCacheTTL configures how long successful artifact downloads are cached.
// Set to 0 to disable caching. Safe to call concurrently with Get* methods.
func (ac *ArtifactClient) SetCacheTTL(ttl time.Duration) {
	ac.cacheTTL.Store(int64(ttl))
}

// ClearCache evicts all cached artifact results.
func (ac *ArtifactClient) ClearCache() {
	ac.mu.Lock()
	defer ac.mu.Unlock()
	ac.cache = make(map[string]*artifactCacheEntry)
}

func artifactCacheKey(owner, name, alias string) string {
	return owner + "/" + name + "/" + alias
}

// PublishOptions configures an artifact publish request.
type PublishOptions struct {
	Version     string            // Version string; server generates timestamp if empty
	Description string            // Human-readable description
	MakeActive  *bool             // Whether to update the 'active' alias (default true)
	Alias       string            // Additional alias to create
	Labels      map[string]string // Custom key-value labels
}

// PublishResponse is the server's response after publishing an artifact.
type PublishResponse struct {
	PluginName   *string `json:"pluginName"`
	ArtifactName string  `json:"artifactName"`
	Version      string  `json:"version"`
	IsActive     bool    `json:"isActive"`
	SHA256       string  `json:"sha256"`
}

// ArtifactManifest holds metadata about a downloaded artifact, extracted from response headers.
type ArtifactManifest struct {
	Version      string
	Description  string
	ContentType  string
	SHA256       string
	Labels       map[string]string
	SourcePrefix string
}

// FetchedArtifact holds a fully downloaded artifact with its metadata.
type FetchedArtifact struct {
	Data     []byte
	Manifest ArtifactManifest
}

// ArtifactStream holds a streaming artifact download. The caller must close Reader when done.
type ArtifactStream struct {
	Reader   io.ReadCloser
	Manifest ArtifactManifest
}

// PublishArtifact uploads an artifact to the plugin's namespace.
func (ac *ArtifactClient) PublishArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	return ac.publish(ctx, ac.owner, name, data, contentType, opts)
}

// PublishSharedArtifact uploads an artifact to the shared namespace.
func (ac *ArtifactClient) PublishSharedArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	return ac.publish(ctx, sharedOwner, name, data, contentType, opts)
}

// GetArtifact downloads an artifact from the plugin's namespace.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) GetArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error) {
	return ac.get(ctx, ac.owner, name, alias)
}

// GetSharedArtifact downloads an artifact from the shared namespace.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) GetSharedArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error) {
	return ac.get(ctx, sharedOwner, name, alias)
}

// StreamArtifact opens a streaming download from the plugin's namespace.
// The caller must close the returned ArtifactStream.Reader when done.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) StreamArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error) {
	return ac.stream(ctx, ac.owner, name, alias)
}

// StreamSharedArtifact opens a streaming download from the shared namespace.
// The caller must close the returned ArtifactStream.Reader when done.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) StreamSharedArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error) {
	return ac.stream(ctx, sharedOwner, name, alias)
}

func (ac *ArtifactClient) publish(ctx context.Context, owner, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)

	// Add the file part
	part, err := writer.CreateFormFile("file", name)
	if err != nil {
		return nil, fmt.Errorf("failed to create multipart file: %w", err)
	}
	if _, err := io.Copy(part, data); err != nil {
		return nil, fmt.Errorf("failed to write artifact data: %w", err)
	}

	// Add optional fields
	if opts != nil {
		if opts.Version != "" {
			writer.WriteField("version", opts.Version)
		}
		if opts.Description != "" {
			writer.WriteField("description", opts.Description)
		}
		if opts.MakeActive != nil {
			writer.WriteField("makeActive", fmt.Sprintf("%t", *opts.MakeActive))
		}
		if opts.Alias != "" {
			writer.WriteField("alias", opts.Alias)
		}
		for k, v := range opts.Labels {
			writer.WriteField(k, v)
		}
	}

	if err := writer.Close(); err != nil {
		return nil, fmt.Errorf("failed to close multipart writer: %w", err)
	}

	url := fmt.Sprintf("%s%s/%s/%s", ac.coreURL, artifactsPath, owner, name)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, &body)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	setArtifactAuthHeaders(req)

	resp, err := ac.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("publish request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != http.StatusCreated {
		return nil, fmt.Errorf("publish returned HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	var result PublishResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse publish response: %w", err)
	}

	ac.invalidateCache(owner, name)

	return &result, nil
}

func (ac *ArtifactClient) get(ctx context.Context, owner, name, alias string) (*FetchedArtifact, error) {
	if alias == "" {
		alias = activeAlias
	}
	cacheKey := artifactCacheKey(owner, name, alias)

	if fetched, err, ok := ac.cacheGet(cacheKey); ok {
		return fetched, err
	}

	v, err, _ := ac.flight.Do(cacheKey, func() (any, error) {
		// Re-check the cache inside the flight to absorb the case where a
		// concurrent caller populated it while we were waiting.
		if fetched, err, ok := ac.cacheGet(cacheKey); ok {
			return fetched, err
		}
		fetched, err := ac.fetch(ctx, owner, name, alias)
		ac.cachePut(cacheKey, fetched, err)
		return fetched, err
	})
	if v == nil {
		return nil, err
	}
	return v.(*FetchedArtifact), err
}

// cacheGet returns a cached entry if it is still valid. The third return is
// true when the cache hit is fresh.
func (ac *ArtifactClient) cacheGet(key string) (*FetchedArtifact, error, bool) {
	ac.mu.RLock()
	defer ac.mu.RUnlock()
	entry, ok := ac.cache[key]
	if !ok || !time.Now().Before(entry.expiresAt) {
		return nil, nil, false
	}
	return entry.artifact, entry.err, true
}

// cachePut stores a fetched artifact (or fetch error) with the appropriate TTL.
// Skipped entirely when the success TTL is zero (caching disabled).
func (ac *ArtifactClient) cachePut(key string, fetched *FetchedArtifact, fetchErr error) {
	ttl := time.Duration(ac.cacheTTL.Load())
	if fetchErr != nil {
		ttl = time.Duration(ac.failureCacheTTL.Load())
	}
	if ttl <= 0 {
		return
	}
	ac.mu.Lock()
	ac.cache[key] = &artifactCacheEntry{
		artifact:  fetched,
		err:       fetchErr,
		expiresAt: time.Now().Add(ttl),
	}
	ac.mu.Unlock()
}

func (ac *ArtifactClient) fetch(ctx context.Context, owner, name, alias string) (*FetchedArtifact, error) {
	stream, err := ac.stream(ctx, owner, name, alias)
	if err != nil {
		return nil, err
	}
	defer stream.Reader.Close()

	data, err := io.ReadAll(stream.Reader)
	if err != nil {
		return nil, fmt.Errorf("failed to read artifact data: %w", err)
	}

	return &FetchedArtifact{
		Data:     data,
		Manifest: stream.Manifest,
	}, nil
}

// invalidateCache removes cached entries for the given artifact across all aliases.
// Returns true if any entries were evicted.
func (ac *ArtifactClient) invalidateCache(owner, name string) bool {
	prefix := owner + "/" + name + "/"
	evicted := false
	ac.mu.Lock()
	defer ac.mu.Unlock()
	for key := range ac.cache {
		if strings.HasPrefix(key, prefix) {
			delete(ac.cache, key)
			evicted = true
		}
	}
	return evicted
}

func (ac *ArtifactClient) stream(ctx context.Context, owner, name, alias string) (*ArtifactStream, error) {
	if alias == "" {
		alias = activeAlias
	}

	url := fmt.Sprintf("%s%s/%s/%s/versions/%s", ac.coreURL, artifactsPath, owner, name, alias)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	setArtifactAuthHeaders(req)

	resp, err := ac.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("download request failed: %w", err)
	}

	if resp.StatusCode == http.StatusNotFound {
		resp.Body.Close()
		return nil, fmt.Errorf("artifact not found: %s/%s (alias %q)", owner, name, alias)
	}
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		return nil, fmt.Errorf("download returned HTTP %d: %s", resp.StatusCode, string(body))
	}

	manifest := parseManifestHeaders(resp)

	return &ArtifactStream{
		Reader:   resp.Body,
		Manifest: manifest,
	}, nil
}

func parseManifestHeaders(resp *http.Response) ArtifactManifest {
	manifest := ArtifactManifest{
		Version:      resp.Header.Get("X-Artifact-Version"),
		SHA256:       resp.Header.Get("X-Artifact-Sha256"),
		Description:  resp.Header.Get("X-Artifact-Description"),
		ContentType:  resp.Header.Get("Content-Type"),
		SourcePrefix: resp.Header.Get("X-Artifact-Source-Prefix"),
	}

	if labelsJSON := resp.Header.Get("X-Artifact-Labels"); labelsJSON != "" {
		var labels map[string]string
		if err := json.Unmarshal([]byte(labelsJSON), &labels); err == nil {
			manifest.Labels = labels
		}
	}

	return manifest
}

func setArtifactAuthHeaders(req *http.Request) {
	req.Header.Set("X-User-Permissions", "Admin")
	req.Header.Set("X-User-Name", "go-plugin")
}

// ArtifactDefault describes default data to publish when an artifact is not found.
type ArtifactDefault struct {
	Data        []byte
	ContentType string
	Description string
}

// OnUpdateFetch registers a callback that automatically fetches the artifact and passes
// it to the handler when the version changes or on startup.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) OnUpdateFetch(artifactName string, alias string, handler func(*FetchedArtifact, error)) {
	ac.onUpdateFetch(ac.owner, artifactName, alias, nil, handler)
}

// OnUpdateFetchWithDefault is like OnUpdateFetch, but publishes the provided default
// data if the artifact is not found, then re-fetches.
func (ac *ArtifactClient) OnUpdateFetchWithDefault(artifactName string, alias string, defaultArtifact ArtifactDefault, handler func(*FetchedArtifact, error)) {
	ac.onUpdateFetch(ac.owner, artifactName, alias, &defaultArtifact, handler)
}

// OnSharedUpdateFetch registers a callback that automatically fetches a shared artifact.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) OnSharedUpdateFetch(artifactName string, alias string, handler func(*FetchedArtifact, error)) {
	ac.onUpdateFetch(sharedOwner, artifactName, alias, nil, handler)
}

// OnSharedUpdateFetchWithDefault is like OnSharedUpdateFetch, but publishes a default if not found.
func (ac *ArtifactClient) OnSharedUpdateFetchWithDefault(artifactName string, alias string, defaultArtifact ArtifactDefault, handler func(*FetchedArtifact, error)) {
	ac.onUpdateFetch(sharedOwner, artifactName, alias, &defaultArtifact, handler)
}

func (ac *ArtifactClient) onUpdateFetch(owner, artifactName, alias string, defaultArtifact *ArtifactDefault, handler func(*FetchedArtifact, error)) {
	fetchAlias := alias
	if fetchAlias == "" {
		fetchAlias = activeAlias
	}
	fetchHandler := func(event VersionChangedEvent) {
		ctx := context.Background()
		fetched, err := ac.get(ctx, owner, artifactName, fetchAlias)
		if err != nil && defaultArtifact != nil {
			log.Printf("Artifact %s/%s not found, publishing default", owner, artifactName)
			makeActive := true
			_, pubErr := ac.publish(ctx, owner, artifactName,
				bytes.NewReader(defaultArtifact.Data),
				defaultArtifact.ContentType,
				&PublishOptions{
					Version:     "1",
					Description: defaultArtifact.Description,
					MakeActive:  &makeActive,
				})
			if pubErr != nil {
				log.Printf("Failed to publish default artifact %s/%s: %v", owner, artifactName, pubErr)
				handler(nil, fmt.Errorf("artifact not found and default publish failed: %w", pubErr))
				return
			}
			fetched, err = ac.get(ctx, owner, artifactName, fetchAlias)
		}
		handler(fetched, err)
	}

	if alias == "" {
		alias = activeAlias
	}
	ac.callbackMu.Lock()
	defer ac.callbackMu.Unlock()
	ac.callbacks = append(ac.callbacks, artifactCallback{
		owner:        owner,
		artifactName: artifactName,
		alias:        alias,
		handler:      fetchHandler,
	})
}

// OnUpdate registers a callback that fires when the named artifact's version changes.
// The callback runs after the cache has been invalidated, so GetArtifact inside the
// callback will fetch the latest version. Owner defaults to the client's plugin owner.
// Alias defaults to "active" if empty.
func (ac *ArtifactClient) OnUpdate(artifactName string, alias string, handler func(VersionChangedEvent)) {
	ac.registerCallback(ac.owner, artifactName, alias, handler)
}

// OnSharedUpdate registers a callback for version changes to a shared artifact.
func (ac *ArtifactClient) OnSharedUpdate(artifactName string, alias string, handler func(VersionChangedEvent)) {
	ac.registerCallback(sharedOwner, artifactName, alias, handler)
}

func (ac *ArtifactClient) registerCallback(owner, artifactName, alias string, handler func(VersionChangedEvent)) {
	if alias == "" {
		alias = activeAlias
	}
	ac.callbackMu.Lock()
	defer ac.callbackMu.Unlock()
	ac.callbacks = append(ac.callbacks, artifactCallback{
		owner:        owner,
		artifactName: artifactName,
		alias:        alias,
		handler:      handler,
	})
}

// fireCallbacks invokes all registered callbacks matching the event.
func (ac *ArtifactClient) fireCallbacks(event VersionChangedEvent) {
	owner := event.Owner()
	ac.callbackMu.Lock()
	snapshot := make([]artifactCallback, len(ac.callbacks))
	copy(snapshot, ac.callbacks)
	ac.callbackMu.Unlock()

	for _, cb := range snapshot {
		if cb.owner == owner && cb.artifactName == event.ArtifactName && cb.alias == event.Alias {
			func() {
				defer func() {
					if r := recover(); r != nil {
						log.Printf("Artifact update callback panicked for %s/%s (alias %s): %v", owner, event.ArtifactName, event.Alias, r)
					}
				}()
				cb.handler(event)
				log.Printf("Artifact update callback completed for %s/%s version=%s", owner, event.ArtifactName, event.NewVersion)
			}()
		}
	}
}

// FireStartupCallbacks invokes registered callbacks that have not yet been startup-fired.
// Each callback receives a synthetic event with empty version fields. This method can be
// called multiple times — it only fires callbacks added since the previous call.
func (ac *ArtifactClient) FireStartupCallbacks() {
	ac.callbackMu.Lock()
	startIdx := ac.startupFiredIdx
	unfired := make([]artifactCallback, len(ac.callbacks)-startIdx)
	copy(unfired, ac.callbacks[startIdx:])
	ac.startupFiredIdx = len(ac.callbacks)
	ac.callbackMu.Unlock()

	for _, cb := range unfired {
		prefix := pluginPrefix + cb.owner
		if cb.owner == sharedOwner {
			prefix = sharedOwner
		}
		event := VersionChangedEvent{
			Prefix:       prefix,
			ArtifactName: cb.artifactName,
			Alias:        cb.alias,
		}
		func() {
			defer func() {
				if r := recover(); r != nil {
					log.Printf("Artifact startup callback panicked for %s/%s (alias %s): %v", cb.owner, cb.artifactName, cb.alias, r)
				}
			}()
			cb.handler(event)
		}()
	}
}

// StartArtifactListener subscribes to artifact version change events via Valkey pub/sub.
// It invalidates cached artifacts and fires registered callbacks when versions change.
// The listener runs until the context is cancelled.
func (ac *ArtifactClient) StartArtifactListener(ctx context.Context, wg *sync.WaitGroup, queue *ActionEventQueue) {
	wg.Go(func() {
		for {
			if ctx.Err() != nil {
				return
			}
			ac.runArtifactListener(ctx, queue)
			// Reconnect after 5 seconds if the subscription drops
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
				log.Println("Reconnecting to artifact version change channel...")
			}
		}
	})
}

func (ac *ArtifactClient) runArtifactListener(ctx context.Context, queue *ActionEventQueue) {
	pubsub := queue.Subscribe(ctx, versionChangedChannel)
	defer pubsub.Close()

	ch := pubsub.Channel()
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-ch:
			if !ok {
				return
			}
			ac.handleVersionChanged(msg.Payload)
		}
	}
}

func (ac *ArtifactClient) handleVersionChanged(payload string) {
	var event VersionChangedEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		log.Printf("Failed to parse artifact version change event: %v", err)
		return
	}

	owner := event.Owner()

	// Only process events relevant to this plugin or shared artifacts
	if owner != ac.owner && owner != sharedOwner {
		return
	}

	log.Printf("Artifact version changed: %s/%s alias=%s (%s -> %s)",
		owner, event.ArtifactName, event.Alias, event.PreviousVersion, event.NewVersion)

	// Invalidate cache first so callbacks see fresh data
	if ac.invalidateCache(owner, event.ArtifactName) {
		log.Printf("Invalidated cached artifact: %s/%s", owner, event.ArtifactName)
	}

	// Fire registered callbacks
	ac.fireCallbacks(event)
}
