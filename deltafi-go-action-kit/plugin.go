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
// ABOUTME: Plugin orchestrator that manages the full action kit lifecycle.
// ABOUTME: Handles configuration, action registration, startup, and graceful shutdown.

package actionkit

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

const healthProbeFile = "/tmp/running"

// PluginConfig holds optional overrides for plugin configuration.
// Zero values are ignored and defaults from environment variables are used.
type PluginConfig struct {
	CoreURL         string
	ValkeyURL       string
	ValkeyPassword  string
	MinioURL        string
	MinioAccessKey  string
	MinioSecretKey  string
	MinioBucket     string
	MinioUseSSL     bool
	Image           string
	ImagePullSecret string
	Coordinates     *PluginCoordinates
	DisplayName     string
	Description     string
	Domain          string // Action name prefix (e.g. "org.deltafi.go-hello-world"), mirrors Java's package name
}

// Plugin is the main entry point for a DeltaFi action kit plugin.
type Plugin struct {
	config           PluginConfig
	coordinates      PluginCoordinates
	domain           string
	actions          []registeredAction
	flows            []FlowPlan
	variables        []Variable
	workerCounts     map[string]int
	suppliers        map[string]LookupTableSupplier
	artifactWatches  []artifactWatch
	integrationTests []json.RawMessage
}

type artifactWatch struct {
	name            string
	defaultArtifact *ArtifactDefault
	handler         func(*FetchedArtifact, error)
	shared          bool
}

// NewPlugin creates a new Plugin with configuration from environment variables
// and optional overrides.
func NewPlugin(config *PluginConfig) (*Plugin, error) {
	cfg := PluginConfig{
		CoreURL:         envOrDefault("CORE_URL", "http://deltafi-core-service"),
		ValkeyURL:       envOrDefault("VALKEY_URL", "deltafi-valkey-master:6379"),
		ValkeyPassword:  envOrDefault("VALKEY_PASSWORD", ""),
		MinioURL:        envOrDefault("MINIO_URL", "deltafi-minio:9000"),
		MinioAccessKey:  envOrDefault("MINIO_ACCESSKEY", "deltafi"),
		MinioSecretKey:  envOrDefault("MINIO_SECRETKEY", "password"),
		MinioBucket:     envOrDefault("STORAGE_BUCKET_NAME", "storage"),
		Image:           envOrDefault("IMAGE", ""),
		ImagePullSecret: envOrDefault("IMAGE_PULL_SECRET", ""),
		DisplayName:     envOrDefault("PROJECT_NAME", ""),
		Description:     "",
	}

	// Apply overrides from config
	if config != nil {
		if config.CoreURL != "" {
			cfg.CoreURL = config.CoreURL
		}
		if config.ValkeyURL != "" {
			cfg.ValkeyURL = config.ValkeyURL
		}
		if config.ValkeyPassword != "" {
			cfg.ValkeyPassword = config.ValkeyPassword
		}
		if config.MinioURL != "" {
			cfg.MinioURL = config.MinioURL
		}
		if config.MinioAccessKey != "" {
			cfg.MinioAccessKey = config.MinioAccessKey
		}
		if config.MinioSecretKey != "" {
			cfg.MinioSecretKey = config.MinioSecretKey
		}
		if config.MinioBucket != "" {
			cfg.MinioBucket = config.MinioBucket
		}
		if config.MinioUseSSL {
			cfg.MinioUseSSL = true
		}
		if config.Image != "" {
			cfg.Image = config.Image
		}
		if config.ImagePullSecret != "" {
			cfg.ImagePullSecret = config.ImagePullSecret
		}
		if config.DisplayName != "" {
			cfg.DisplayName = config.DisplayName
		}
		if config.Description != "" {
			cfg.Description = config.Description
		}
		if config.Coordinates != nil {
			cfg.Coordinates = config.Coordinates
		}
		if config.Domain != "" {
			cfg.Domain = config.Domain
		}
	}

	coords := PluginCoordinates{
		GroupID:    envOrDefault("PROJECT_GROUP", ""),
		ArtifactID: envOrDefault("PROJECT_NAME", ""),
		Version:    envOrDefault("PROJECT_VERSION", ""),
	}
	if cfg.Coordinates != nil {
		coords = *cfg.Coordinates
	}

	return &Plugin{
		config:      cfg,
		coordinates: coords,
		domain:      cfg.Domain,
		flows:       []FlowPlan{},
		variables:   []Variable{},
	}, nil
}

// SetWorkerCount configures the number of concurrent workers for a named action.
// Must be called before Run. Defaults to 1 if not set.
func (p *Plugin) SetWorkerCount(actionName string, count int) {
	if p.workerCounts == nil {
		p.workerCounts = make(map[string]int)
	}
	p.workerCounts[actionName] = count
}

// workerCount returns the configured worker count for an action.
// Priority: SetWorkerCount > ACTIONS_ACTIONTHREADS_{NAME} env var > ACTION_THREADS env var > default of 1.
func (p *Plugin) workerCount(actionName string) int {
	if count, ok := p.workerCounts[actionName]; ok && count > 0 {
		return count
	}
	envName := strings.NewReplacer(".", "_", "-", "_").Replace(actionName)
	perActionEnv := "ACTIONS_ACTIONTHREADS_" + strings.ToUpper(envName)
	if s := os.Getenv(perActionEnv); s != "" {
		if n, err := strconv.Atoi(s); err == nil && n > 0 {
			return n
		}
	}
	if s := os.Getenv("ACTION_THREADS"); s != "" {
		if n, err := strconv.Atoi(s); err == nil && n > 0 {
			return n
		}
	}
	return 1
}

// qualifiedName returns the full action name, prefixed with the domain if set.
func (p *Plugin) qualifiedName(name string) string {
	if p.domain != "" {
		return p.domain + "." + name
	}
	return name
}

// RegisterAction registers an action from a Registration. The action must implement
// TransformAction, EgressAction, or TimedIngressAction.
// If the plugin has a domain configured, the action name is qualified as "domain.name".
func (p *Plugin) RegisterAction(reg *Registration) error {
	actionType, err := detectActionType(reg.Action)
	if err != nil {
		return fmt.Errorf("action %q: %w", reg.Name, err)
	}
	desc := reg.description
	if desc == "" {
		desc = "No description provided"
	}
	p.actions = append(p.actions, registeredAction{
		name:        p.qualifiedName(reg.Name),
		action:      reg.Action,
		actionType:  actionType,
		description: desc,
		paramType:   reg.paramType,
		options:     reg.options,
	})
	return nil
}

// RegisterFlow adds a flow plan to the plugin.
func (p *Plugin) RegisterFlow(flow FlowPlan) {
	p.flows = append(p.flows, flow)
}

// RegisterVariable adds a variable definition to the plugin.
func (p *Plugin) RegisterVariable(v Variable) {
	p.variables = append(p.variables, v)
}

// WatchArtifact registers a plugin-level artifact watch that fires before registration.
// If defaultArtifact is non-nil and the artifact does not exist, the default is published.
// The handler is called with the current artifact contents on startup (before registration)
// and again each time the artifact is updated.
func (p *Plugin) WatchArtifact(name string, defaultArtifact *ArtifactDefault, handler func(*FetchedArtifact, error)) {
	p.artifactWatches = append(p.artifactWatches, artifactWatch{
		name: name, defaultArtifact: defaultArtifact, handler: handler,
	})
}

// WatchSharedArtifact is like WatchArtifact but watches the shared namespace.
func (p *Plugin) WatchSharedArtifact(name string, defaultArtifact *ArtifactDefault, handler func(*FetchedArtifact, error)) {
	p.artifactWatches = append(p.artifactWatches, artifactWatch{
		name: name, defaultArtifact: defaultArtifact, handler: handler, shared: true,
	})
}

// RegisterLookupTableSupplier registers a supplier that provides lookup table data.
func (p *Plugin) RegisterLookupTableSupplier(supplier LookupTableSupplier) {
	if p.suppliers == nil {
		p.suppliers = make(map[string]LookupTableSupplier)
	}
	p.suppliers[supplier.Table().Name] = supplier
}

// lookupTables returns the lookup table definitions from all registered suppliers.
func (p *Plugin) lookupTables() []LookupTableDefinition {
	if len(p.suppliers) == 0 {
		return []LookupTableDefinition{}
	}
	tables := make([]LookupTableDefinition, 0, len(p.suppliers))
	for _, s := range p.suppliers {
		tables = append(tables, s.Table())
	}
	return tables
}

// RunPlugin creates a plugin, registers all actions from the global registry,
// loads flows and variables from the "flows/" directory, and runs the full lifecycle.
// This is the standard entry point for DeltaFi Go plugins.
func RunPlugin(config *PluginConfig) {
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	plugin, err := NewPlugin(config)
	if err != nil {
		log.Fatalf("Failed to create plugin: %v", err)
	}

	for _, entry := range All() {
		if err := plugin.RegisterAction(entry); err != nil {
			log.Fatalf("Failed to register %s: %v", entry.Name, err)
		}
		log.Printf("Registered action: %s", plugin.qualifiedName(entry.Name))
	}

	variables, err := loadVariables("flows")
	if err != nil {
		log.Fatalf("Failed to load variables: %v", err)
	}
	for _, v := range variables {
		plugin.RegisterVariable(v)
	}

	flows, err := loadFlowsFromDirectory("flows")
	if err != nil {
		log.Fatalf("Failed to load flows: %v", err)
	}
	for _, flow := range flows {
		plugin.RegisterFlow(flow)
	}

	for _, supplier := range AllSuppliers() {
		plugin.RegisterLookupTableSupplier(supplier)
		log.Printf("Registered lookup table supplier: %s", supplier.Table().Name)
	}

	for _, w := range registeredWatches {
		if w.shared {
			plugin.WatchSharedArtifact(w.name, w.defaultArtifact, w.handler)
		} else {
			plugin.WatchArtifact(w.name, w.defaultArtifact, w.handler)
		}
	}

	integrationTests, err := loadIntegrationTests("integration")
	if err != nil {
		log.Fatalf("Failed to load integration tests: %v", err)
	}
	plugin.integrationTests = integrationTests

	log.Printf("Loaded %d flow(s), %d variable(s), %d integration test(s)", len(flows), len(variables), len(integrationTests))

	if err := plugin.Run(context.Background()); err != nil {
		log.Fatalf("Plugin error: %v", err)
	}
}

// loadFlowsFromDirectory loads all flow JSON files from a directory (excluding variables.json).
func loadFlowsFromDirectory(dir string) ([]FlowPlan, error) {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return nil, nil
	}

	files, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil {
		return nil, fmt.Errorf("failed to list flow files: %w", err)
	}

	var flows []FlowPlan
	for _, file := range files {
		if filepath.Base(file) == "variables.json" {
			continue
		}

		data, err := os.ReadFile(file)
		if err != nil {
			return nil, fmt.Errorf("failed to read flow file %s: %w", file, err)
		}

		var flow FlowPlan
		if err := json.Unmarshal(data, &flow); err != nil {
			return nil, fmt.Errorf("failed to parse flow file %s: %w", file, err)
		}

		flows = append(flows, flow)
	}

	return flows, nil
}

// loadVariables loads variables from variables.json in the given directory.
func loadVariables(dir string) ([]Variable, error) {
	path := filepath.Join(dir, "variables.json")
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil, nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read variables file: %w", err)
	}

	var variables []Variable
	if err := json.Unmarshal(data, &variables); err != nil {
		return nil, fmt.Errorf("failed to parse variables file: %w", err)
	}

	return variables, nil
}

// loadIntegrationTests loads all JSON integration test files from a directory.
func loadIntegrationTests(dir string) ([]json.RawMessage, error) {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return []json.RawMessage{}, nil
	}

	files, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil {
		return nil, fmt.Errorf("failed to list integration test files: %w", err)
	}

	tests := make([]json.RawMessage, 0, len(files))
	for _, file := range files {
		data, err := os.ReadFile(file)
		if err != nil {
			return nil, fmt.Errorf("failed to read integration test file %s: %w", file, err)
		}

		if !json.Valid(data) {
			return nil, fmt.Errorf("invalid JSON in integration test file %s", file)
		}

		tests = append(tests, json.RawMessage(data))
	}

	return tests, nil
}

// Run starts the full plugin lifecycle: connect, register, poll, heartbeat, shutdown.
func (p *Plugin) Run(ctx context.Context) error {
	// Signal handling
	ctx, stop := signal.NotifyContext(ctx, syscall.SIGTERM, syscall.SIGINT)
	defer stop()

	// Connect to Redis/Valkey
	log.Printf("Connecting to Redis at %s...", p.config.ValkeyURL)
	queue, err := NewActionEventQueue(p.config.ValkeyURL, p.config.ValkeyPassword)
	if err != nil {
		return fmt.Errorf("failed to connect to redis: %w", err)
	}
	defer queue.Close()
	log.Println("Connected to Redis")

	// Connect to MinIO
	log.Printf("Connecting to MinIO at %s...", p.config.MinioURL)
	contentService, err := NewContentService(
		p.config.MinioURL,
		p.config.MinioAccessKey,
		p.config.MinioSecretKey,
		p.config.MinioBucket,
		p.config.MinioUseSSL,
	)
	if err != nil {
		return fmt.Errorf("failed to connect to minio: %w", err)
	}
	log.Println("Connected to MinIO")

	// Create lookup client
	lookupClient := NewLookupClient(p.config.CoreURL)

	// Create artifact client
	artifactOwner := p.coordinates.GroupID + "." + p.coordinates.ArtifactID
	artifactClient := NewArtifactClient(p.config.CoreURL, artifactOwner)

	// Start lookup table supplier runner before registration so it can respond
	// to supplier queries that core may issue during registration processing.
	var wg sync.WaitGroup
	if len(p.suppliers) > 0 {
		startLookupTableSupplierRunner(ctx, &wg, queue, p.suppliers)
		log.Printf("Started lookup table supplier runner for %d table(s)", len(p.suppliers))
	}

	// Set up and fire plugin-level artifact watches before registration
	for _, w := range p.artifactWatches {
		if w.defaultArtifact != nil {
			if w.shared {
				artifactClient.OnSharedUpdateFetchWithDefault(w.name, "", *w.defaultArtifact, w.handler)
			} else {
				artifactClient.OnUpdateFetchWithDefault(w.name, "", *w.defaultArtifact, w.handler)
			}
		} else {
			if w.shared {
				artifactClient.OnSharedUpdateFetch(w.name, "", w.handler)
			} else {
				artifactClient.OnUpdateFetch(w.name, "", w.handler)
			}
		}
	}
	if len(p.artifactWatches) > 0 {
		artifactClient.FireStartupCallbacks()
		log.Printf("Loaded %d plugin-level artifact watch(es)", len(p.artifactWatches))
	}

	// Build and post registration
	reg := buildRegistration(p)
	log.Printf("Registering plugin %s:%s:%s with %d actions, %d flows, %d variables...",
		p.coordinates.GroupID, p.coordinates.ArtifactID, p.coordinates.Version,
		len(reg.Actions), len(reg.FlowPlans), len(reg.Variables))

	if err := postRegistration(ctx, p.config.CoreURL, reg); err != nil {
		return fmt.Errorf("failed to register plugin: %w", err)
	}

	// Upload initial lookup table data from suppliers
	if len(p.suppliers) > 0 {
		uploadSupplierData(ctx, lookupClient, p.suppliers)
	}

	// Allow actions to register artifact version change callbacks
	for _, ra := range p.actions {
		if aa, ok := ra.action.(ArtifactAware); ok {
			aa.ConfigureArtifacts(artifactClient)
		}
	}

	// Fire startup callbacks for artifact version handlers and start listener
	artifactClient.FireStartupCallbacks()
	artifactClient.StartArtifactListener(ctx, &wg, queue)
	log.Println("Artifact version change listener started")

	// Collect action names for heartbeat
	actionNames := make([]string, len(p.actions))
	for i, a := range p.actions {
		actionNames[i] = a.name
	}

	// Resolve app name for tracking
	appName := envOrDefault("APP_NAME", "")
	if appName == "" {
		appName, _ = os.Hostname()
	}

	// Start workers
	tracker := newInFlightTracker()

	for _, ra := range p.actions {
		count := p.workerCount(ra.name)
		w := &actionWorker{
			name:           ra.name,
			action:         ra.action,
			actionType:     ra.actionType,
			queue:          queue,
			contentService: contentService,
			lookupClient:   lookupClient,
			artifactClient: artifactClient,
			workerCount:    count,
			tracker:        tracker,
			appName:        appName,
		}
		w.start(ctx, &wg)
		log.Printf("Started %d worker(s) for %s (%s)", count, ra.name, ra.actionType)
	}

	// Start heartbeat
	startHeartbeat(ctx, &wg, queue, actionNames, tracker, appName)
	log.Println("Heartbeat started")

	// Create health probe file
	if err := os.WriteFile(healthProbeFile, []byte("running"), 0644); err != nil {
		log.Printf("Warning: failed to create health probe file: %v", err)
	}

	log.Println("Plugin running, waiting for work...")

	// Block until signal
	<-ctx.Done()
	log.Println("Shutdown signal received, draining workers...")

	// Wait for workers with timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		log.Println("All workers drained successfully")
	case <-time.After(30 * time.Second):
		log.Println("Warning: worker drain timed out after 30s")
	}

	// Remove health probe
	os.Remove(healthProbeFile)

	return nil
}
