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
// ABOUTME: Package-level functions for lookup queries and artifact operations.
// ABOUTME: Uses the plugin's base context and clients, initialized during RunPlugin.

package actionkit

import (
	"context"
	"fmt"
	"io"
)

// globalState holds plugin-level clients and context for package-level functions.
var globalState struct {
	ctx            context.Context
	lookupClient   LookupClientBase
	artifactClient ArtifactClientBase
}

// initGlobalState is called during plugin startup to set up package-level state.
func initGlobalState(ctx context.Context, lookupClient LookupClientBase, artifactClient ArtifactClientBase) {
	globalState.ctx = ctx
	globalState.lookupClient = lookupClient
	globalState.artifactClient = artifactClient
}

// globalContext returns the plugin's base context, defaulting to Background if not initialized.
func globalContext() context.Context {
	if globalState.ctx != nil {
		return globalState.ctx
	}
	return context.Background()
}

// --- Lookup ---

// Lookup queries a lookup table by name with the given query builder.
// Pass nil for query to fetch all rows.
func Lookup(tableName string, query *LookupQueryBuilder) (*LookupResults, error) {
	if globalState.lookupClient == nil {
		return nil, fmt.Errorf("lookup client not initialized: Lookup is only available after plugin startup")
	}
	var opts []LookupOption
	if query != nil {
		opts = query.toLookupOptions()
	}
	results, err := globalState.lookupClient.Lookup(globalContext(), tableName, opts...)
	if err != nil {
		return nil, err
	}
	if query != nil {
		results = query.applyExclusions(results)
	}
	return results, nil
}

// --- Artifacts ---

// GetArtifact downloads an artifact from the plugin's namespace.
// Alias defaults to "active" if empty.
func GetArtifact(name, alias string) (*FetchedArtifact, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: GetArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.GetArtifact(globalContext(), name, alias)
}

// GetSharedArtifact downloads an artifact from the shared namespace.
// Alias defaults to "active" if empty.
func GetSharedArtifact(name, alias string) (*FetchedArtifact, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: GetSharedArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.GetSharedArtifact(globalContext(), name, alias)
}

// PublishArtifact uploads an artifact to the plugin's namespace.
func PublishArtifact(name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: PublishArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.PublishArtifact(globalContext(), name, data, contentType, opts)
}

// PublishSharedArtifact uploads an artifact to the shared namespace.
func PublishSharedArtifact(name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: PublishSharedArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.PublishSharedArtifact(globalContext(), name, data, contentType, opts)
}

// StreamArtifact opens a streaming download from the plugin's namespace.
// The caller must close the returned ArtifactStream.Reader when done.
// Alias defaults to "active" if empty.
func StreamArtifact(name, alias string) (*ArtifactStream, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: StreamArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.StreamArtifact(globalContext(), name, alias)
}

// StreamSharedArtifact opens a streaming download from the shared namespace.
// The caller must close the returned ArtifactStream.Reader when done.
// Alias defaults to "active" if empty.
func StreamSharedArtifact(name, alias string) (*ArtifactStream, error) {
	if globalState.artifactClient == nil {
		return nil, fmt.Errorf("artifact client not initialized: StreamSharedArtifact is only available after plugin startup")
	}
	return globalState.artifactClient.StreamSharedArtifact(globalContext(), name, alias)
}
