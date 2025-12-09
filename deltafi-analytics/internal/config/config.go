/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Fetches configuration from deltafi-core via GraphQL.
// ABOUTME: Provides analyticsGroupName and parquetAnalyticsAgeOffDays for runtime configuration.

package config

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"sync"
	"time"
)

const DefaultAgeOffDays = 30

type CoreConfig struct {
	coreURL            string
	analyticsGroupName string
	ageOffDays         int
	mu                 sync.RWMutex
	logger             *slog.Logger
	client             *http.Client
}

func NewCoreConfig(coreURL string, logger *slog.Logger) *CoreConfig {
	c := &CoreConfig{
		coreURL:    coreURL,
		ageOffDays: DefaultAgeOffDays,
		logger:     logger,
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
	// Fetch on startup
	c.refresh()
	return c
}

// StartPeriodicRefresh starts a background goroutine that refreshes config every minute
func (c *CoreConfig) StartPeriodicRefresh(ctx <-chan struct{}) {
	go func() {
		ticker := time.NewTicker(1 * time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				c.refresh()
			case <-ctx:
				return
			}
		}
	}()
}

// GetAnalyticsGroupName returns the cached analyticsGroupName
func (c *CoreConfig) GetAnalyticsGroupName() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.analyticsGroupName
}

// GetAgeOffDays returns the cached parquetAnalyticsAgeOffDays
func (c *CoreConfig) GetAgeOffDays() int {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.ageOffDays
}

func (c *CoreConfig) refresh() {
	if c.coreURL == "" {
		c.logger.Debug("no core URL configured, skipping config refresh")
		return
	}

	props, err := c.fetchProperties()
	if err != nil {
		c.logger.Warn("failed to fetch properties from core", "error", err)
		return
	}

	c.mu.Lock()
	c.analyticsGroupName = props.analyticsGroupName
	if props.ageOffDays > 0 {
		c.ageOffDays = props.ageOffDays
	}
	c.mu.Unlock()

	c.logger.Info("fetched config from core", "analyticsGroupName", props.analyticsGroupName, "ageOffDays", c.ageOffDays)
}

type coreProperties struct {
	analyticsGroupName string
	ageOffDays         int
}

func (c *CoreConfig) fetchProperties() (coreProperties, error) {
	query := `{"query":"{ getPropertySets { properties { key value } } }"}`

	req, err := http.NewRequest("POST", c.coreURL+"/api/v2/graphql", bytes.NewBufferString(query))
	if err != nil {
		return coreProperties{}, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-Name", "analytics")
	req.Header.Set("X-User-Permissions", "Admin")

	resp, err := c.client.Do(req)
	if err != nil {
		return coreProperties{}, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return coreProperties{}, fmt.Errorf("unexpected status %d: %s", resp.StatusCode, string(body))
	}

	var result struct {
		Data struct {
			GetPropertySets []struct {
				Properties []struct {
					Key   string `json:"key"`
					Value string `json:"value"`
				} `json:"properties"`
			} `json:"getPropertySets"`
		} `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return coreProperties{}, fmt.Errorf("failed to decode response: %w", err)
	}

	var props coreProperties
	for _, ps := range result.Data.GetPropertySets {
		for _, p := range ps.Properties {
			switch p.Key {
			case "analyticsGroupName":
				props.analyticsGroupName = p.Value
			case "parquetAnalyticsAgeOffDays":
				if days, err := strconv.Atoi(p.Value); err == nil && days > 0 {
					props.ageOffDays = days
				}
			}
		}
	}

	return props, nil
}
