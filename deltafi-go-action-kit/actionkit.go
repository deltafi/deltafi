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
// ABOUTME: Core types for the DeltaFi Go Action Kit library.
// ABOUTME: Defines PluginCoordinates, ActionContext, Variable, FlowPlan, and env helpers.

package actionkit

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"sync"
	"time"

	"github.com/google/uuid"
)

// Version is the action kit version, set at build time via -ldflags.
var Version = "0.0.0-dev"

// PluginCoordinates identifies a plugin in the DeltaFi ecosystem.
type PluginCoordinates struct {
	GroupID    string `json:"groupId"`
	ArtifactID string `json:"artifactId"`
	Version    string `json:"version"`
}

// ActionContext holds per-invocation execution context passed to every action.
type ActionContext struct {
	DID            string
	DeltaFileName  string
	DataSource     string
	FlowName       string
	FlowID         string
	ActionName     string
	ActionVersion  string
	Hostname       string
	SystemName     string
	StartTime      time.Time
	Memo           string
	ContentService ContentServiceBase
	LookupClient   LookupClientBase
	ArtifactClient ArtifactClientBase
	Join           *JoinConfiguration
	JoinedDIDs     []string
	savedSegments  []Segment
}

// JoinConfiguration holds join parameters from the flow definition.
// MaxAge is serialized by Java's Jackson as seconds (float64).
type JoinConfiguration struct {
	MaxAge      float64 `json:"maxAge"`
	MinNum      int     `json:"minNum"`
	MaxNum      int     `json:"maxNum"`
	MetadataKey string  `json:"metadataKey"`
}

// ChildContext creates a child context with a new UUID for multi-child splitting.
// The child inherits the parent's flow context and content service.
func (actx *ActionContext) ChildContext() *ActionContext {
	return &ActionContext{
		DID:            uuid.New().String(),
		DeltaFileName:  actx.DeltaFileName,
		DataSource:     actx.DataSource,
		FlowName:       actx.FlowName,
		FlowID:         actx.FlowID,
		ActionName:     actx.ActionName,
		ActionVersion:  actx.ActionVersion,
		Hostname:       actx.Hostname,
		SystemName:     actx.SystemName,
		StartTime:      actx.StartTime,
		Memo:           actx.Memo,
		ContentService: actx.ContentService,
		LookupClient:   actx.LookupClient,
		ArtifactClient: actx.ArtifactClient,
	}
}

// trackSegment records a segment saved during action execution for orphan cleanup.
func (actx *ActionContext) trackSegment(seg Segment) {
	actx.savedSegments = append(actx.savedSegments, seg)
}

// Save stores data in the configured content service and returns an
// ActionContent. Equivalent to calling SaveContent with this context's DID
// and content service, but lets callers avoid plumbing them explicitly.
func (actx *ActionContext) Save(ctx context.Context, data []byte, name, mediaType string) (*ActionContent, error) {
	return SaveContent(ctx, actx.ContentService, actx, data, name, mediaType)
}

// SaveString stores a string in the configured content service and returns an ActionContent.
func (actx *ActionContext) SaveString(ctx context.Context, s, name, mediaType string) (*ActionContent, error) {
	return SaveContentString(ctx, actx.ContentService, actx, s, name, mediaType)
}

// SaveFromReader stores data from an io.Reader in the configured content
// service and returns an ActionContent. Use size = -1 if the size is not known
// in advance.
func (actx *ActionContext) SaveFromReader(ctx context.Context, reader io.Reader, size int64, name, mediaType string) (*ActionContent, error) {
	return SaveContentFromReader(ctx, actx.ContentService, actx, reader, size, name, mediaType)
}

// Load fetches and concatenates the bytes for the given content via this
// context's ContentService. The content does not need to be wired to this
// ActionContext — the service is read directly from the receiver — so this
// method works as a fallback when content was constructed without a back-reference.
func (actx *ActionContext) Load(ctx context.Context, content *ActionContent) ([]byte, error) {
	if content == nil {
		return nil, fmt.Errorf("nil content")
	}
	if actx.ContentService == nil {
		return nil, fmt.Errorf("ActionContext has no ContentService")
	}
	return actx.ContentService.Load(ctx, content.Segments)
}

// LoadString fetches the bytes for the given content and returns them as a string.
func (actx *ActionContext) LoadString(ctx context.Context, content *ActionContent) (string, error) {
	data, err := actx.Load(ctx, content)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// LoadReader returns a streaming reader for the given content via this
// context's ContentService. The caller must close the returned reader.
func (actx *ActionContext) LoadReader(ctx context.Context, content *ActionContent) (io.ReadCloser, error) {
	if content == nil {
		return nil, fmt.Errorf("nil content")
	}
	if actx.ContentService == nil {
		return nil, fmt.Errorf("ActionContext has no ContentService")
	}
	return actx.ContentService.LoadReader(ctx, content.Segments)
}

// Errorf returns a new ErrorResult whose cause is the formatted message.
// Use SetContext on the returned result to attach additional detail.
//
//	return input.ActionContext.Errorf("Failed to load content: %v", err), nil
func (actx *ActionContext) Errorf(format string, a ...any) *ErrorResult {
	return NewErrorResult(actx, fmt.Sprintf(format, a...))
}

// Filterf returns a new FilterResult whose message is the formatted message.
//
//	return input.ActionContext.Filterf("Skipping DID with prefix %q", prefix), nil
func (actx *ActionContext) Filterf(format string, a ...any) *FilterResult {
	return NewFilterResult(actx, fmt.Sprintf(format, a...))
}

// Variable defines a plugin variable for flow parameterization.
type Variable struct {
	Name         string `json:"name"`
	Description  string `json:"description"`
	DataType     string `json:"dataType"`
	Required     bool   `json:"required"`
	DefaultValue any    `json:"defaultValue,omitempty"`
	Value        any    `json:"value,omitempty"`
	Masked       bool   `json:"masked,omitempty"`
}

// FlowPlan holds a flow definition as opaque JSON passed through to core.
// Only Name and Type are inspected by the library (for logging).
type FlowPlan struct {
	Name string `json:"-"`
	Type string `json:"-"`
	Raw  json.RawMessage
}

// UnmarshalJSON extracts Name and Type while preserving the full raw JSON.
func (fp *FlowPlan) UnmarshalJSON(data []byte) error {
	fp.Raw = make(json.RawMessage, len(data))
	copy(fp.Raw, data)

	var partial struct {
		Name string `json:"name"`
		Type string `json:"type"`
	}
	if err := json.Unmarshal(data, &partial); err != nil {
		return err
	}
	fp.Name = partial.Name
	fp.Type = partial.Type
	return nil
}

// MarshalJSON emits the raw JSON as-is.
func (fp FlowPlan) MarshalJSON() ([]byte, error) {
	return fp.Raw, nil
}

// SafeStringMap is a concurrency-safe string-to-string map that supports
// atomic replacement of all entries. Useful for caching data from artifacts
// or other external sources that are updated via callbacks.
type SafeStringMap struct {
	mu sync.RWMutex
	m  map[string]string
}

// Store atomically replaces all entries. Pass nil to clear.
func (sm *SafeStringMap) Store(data map[string]string) {
	sm.mu.Lock()
	sm.m = data
	sm.mu.Unlock()
}

// ForEach calls fn for each entry while holding a read lock.
func (sm *SafeStringMap) ForEach(fn func(key, value string)) {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	for k, v := range sm.m {
		fn(k, v)
	}
}

// envOrDefault reads an environment variable, returning fallback if unset or empty.
func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
