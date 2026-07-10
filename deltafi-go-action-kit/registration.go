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

var (
	// Registration retries transient failures (network errors and 5xx) with
	// capped exponential backoff until registrationMaxElapsed is exhausted. The
	// generous default gives core time to become ready during a cluster rollout
	// without the plugin crash-looping. A 4xx is treated as a permanent client
	// error and is not retried.
	registrationInitialBackoff = 500 * time.Millisecond
	registrationMaxBackoff     = 30 * time.Second
	registrationMaxElapsed     = 5 * time.Minute
	registrationAttemptTimeout = 30 * time.Second
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

		if ra.paramDefaults != nil {
			desc.Schema = GenerateSchema(ra.paramDefaults)
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

// postRegistration sends the registration payload to core, retrying transient
// failures (network errors and 5xx responses) with capped exponential backoff
// until registrationMaxElapsed is exhausted or ctx is cancelled. A 4xx response
// is a permanent client error and is returned immediately without retrying.
func postRegistration(ctx context.Context, coreURL string, reg pluginRegistration) error {
	data, err := json.Marshal(reg)
	if err != nil {
		return fmt.Errorf("failed to marshal registration: %w", err)
	}

	url := coreURL + "/plugins"
	deadline := time.Now().Add(registrationMaxElapsed)
	backoff := registrationInitialBackoff

	var lastErr error
	for attempt := 1; ; attempt++ {
		if err := ctx.Err(); err != nil {
			return err
		}

		status, body, err := doRegistrationAttempt(ctx, url, data)
		if err == nil && status >= 200 && status < 300 {
			log.Printf("Plugin registered successfully (HTTP %d)", status)
			return nil
		}

		switch {
		case err != nil:
			lastErr = fmt.Errorf("POST %s failed: %w", url, err)
		case status >= 400 && status < 500:
			// Permanent client error: the payload is wrong, so retrying is futile.
			return fmt.Errorf("POST %s returned HTTP %d (not retrying): %s", url, status, string(body))
		default:
			lastErr = fmt.Errorf("POST %s returned HTTP %d: %s", url, status, string(body))
		}
		log.Printf("Registration error: %v", lastErr)

		// Give up once the next backoff would exceed the overall budget.
		if !time.Now().Add(backoff).Before(deadline) {
			return lastErr
		}
		log.Printf("Retrying registration in %v (attempt %d)...", backoff, attempt+1)
		select {
		case <-time.After(backoff):
		case <-ctx.Done():
			return ctx.Err()
		}
		if backoff *= 2; backoff > registrationMaxBackoff {
			backoff = registrationMaxBackoff
		}
	}
}

// doRegistrationAttempt performs a single registration POST bounded by
// registrationAttemptTimeout, returning the HTTP status and body (or an error
// if the request could not be completed).
func doRegistrationAttempt(ctx context.Context, url string, data []byte) (int, []byte, error) {
	attemptCtx, cancel := context.WithTimeout(ctx, registrationAttemptTimeout)
	defer cancel()

	req, err := http.NewRequestWithContext(attemptCtx, http.MethodPost, url, bytes.NewReader(data))
	if err != nil {
		return 0, nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := platformClient.Do(req)
	if err != nil {
		return 0, nil, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, _ := io.ReadAll(resp.Body)
	return resp.StatusCode, body, nil
}
