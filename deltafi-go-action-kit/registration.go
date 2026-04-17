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
// ABOUTME: Plugin registration with DeltaFi core via HTTP POST.
// ABOUTME: Builds the PluginRegistration payload and handles retry logic.

package actionkit

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"
)

// pluginRegistration is the JSON payload sent to core during registration.
type pluginRegistration struct {
	Image             string                  `json:"image"`
	ImagePullSecret   string                  `json:"imagePullSecret,omitempty"`
	PluginCoordinates PluginCoordinates       `json:"pluginCoordinates"`
	DisplayName       string                  `json:"displayName"`
	Description       string                  `json:"description"`
	ActionKitVersion  string                  `json:"actionKitVersion"`
	Dependencies      []PluginCoordinates     `json:"dependencies"`
	Actions           []actionDescriptor      `json:"actions"`
	Variables         []Variable              `json:"variables"`
	FlowPlans         []json.RawMessage       `json:"flowPlans"`
	LookupTables      []LookupTableDefinition `json:"lookupTables"`
	IntegrationTests  []json.RawMessage       `json:"integrationTests"`
}

// actionDescriptor describes a single action for registration.
type actionDescriptor struct {
	Name         string         `json:"name"`
	Description  string         `json:"description"` // Deprecated: use ActionOptions.Description
	Type         string         `json:"type"`
	SupportsJoin bool           `json:"supportsJoin"`
	Schema       map[string]any `json:"schema"`
	Options      *ActionOptions `json:"actionOptions,omitempty"`
}

// buildRegistration constructs the registration payload from the plugin's state.
func buildRegistration(p *plugin) pluginRegistration {
	actions := make([]actionDescriptor, len(p.actions))
	for i, ra := range p.actions {
		desc := actionDescriptor{
			Name:        ra.name,
			Type:        ra.actionType,
			Description: ra.description,
			Options:     ra.options,
		}

		if ra.dispatcher != nil && ra.dispatcher.hasJoin {
			desc.SupportsJoin = true
		}

		if ra.paramType != nil {
			desc.Schema = GenerateSchema(ra.paramType)
		} else {
			desc.Schema = map[string]any{}
		}

		actions[i] = desc
	}

	flowPlans := make([]json.RawMessage, len(p.flows))
	for i, f := range p.flows {
		flowPlans[i] = f.Raw
	}

	return pluginRegistration{
		Image:             p.config.Image,
		ImagePullSecret:   p.config.ImagePullSecret,
		PluginCoordinates: p.coordinates,
		DisplayName:       displayName(p),
		Description:       p.config.Description,
		ActionKitVersion:  Version,
		Dependencies:      []PluginCoordinates{},
		Actions:           actions,
		Variables:         p.variables,
		FlowPlans:         flowPlans,
		LookupTables:      p.lookupTables(),
		IntegrationTests:  p.integrationTests,
	}
}

func displayName(p *plugin) string {
	if p.config.DisplayName != "" {
		return p.config.DisplayName
	}
	return p.coordinates.ArtifactID
}

// postRegistration sends the registration payload to core with retry.
func postRegistration(ctx context.Context, coreURL string, reg pluginRegistration) error {
	data, err := json.Marshal(reg)
	if err != nil {
		return fmt.Errorf("failed to marshal registration: %w", err)
	}

	url := coreURL + "/plugins"
	backoffs := []time.Duration{500 * time.Millisecond, 2 * time.Second, 5 * time.Second}

	var lastErr error
	for attempt := 0; attempt <= len(backoffs); attempt++ {
		if attempt > 0 {
			log.Printf("Registration attempt %d/%d after %v...", attempt+1, len(backoffs)+1, backoffs[attempt-1])
			select {
			case <-time.After(backoffs[attempt-1]):
			case <-ctx.Done():
				return ctx.Err()
			}
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(data))
		if err != nil {
			return fmt.Errorf("failed to create request: %w", err)
		}
		req.Header.Set("Content-Type", "application/json")

		resp, err := platformClient.Do(req)
		if err != nil {
			lastErr = fmt.Errorf("POST %s failed: %w", url, err)
			log.Printf("Registration error: %v", lastErr)
			continue
		}

		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()

		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			log.Printf("Plugin registered successfully (HTTP %d)", resp.StatusCode)
			return nil
		}

		lastErr = fmt.Errorf("POST %s returned HTTP %d: %s", url, resp.StatusCode, string(body))
		log.Printf("Registration error: %v", lastErr)
	}

	return lastErr
}
