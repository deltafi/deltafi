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
package watcher

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config holds all configuration for the file ingress service
type Config struct {
	WatchDir     string
	URL          string
	IngressAPI   string
	Endpoint     string // Computed from URL and IngressAPI
	Workers      int
	BufferSize   int
	MaxFileSize  int64
	RetryPeriod  time.Duration // Retry period in seconds
	SettlingTime time.Duration // Time in milliseconds to wait for file to settle before processing
}

// LoadConfig loads the configuration from environment variables
func LoadConfig() (*Config, error) {
	url := getEnvOrDefault("DELTAFI_URL", "http://deltafi-core-service")
	ingressAPI := getEnvOrDefault("DELTAFI_INGRESS_API", "/api/v2/deltafile/ingress")

	config := &Config{
		// Default values
		WatchDir:     getEnvOrDefault("DIRWATCHER_WATCH_DIR", "watched-dir"),
		URL:          url,
		IngressAPI:   ingressAPI,
		Endpoint:     url + ingressAPI,
		Workers:      getEnvIntOrDefault("DIRWATCHER_WORKERS", 20),
		MaxFileSize:  getEnvInt64OrDefault("DIRWATCHER_MAX_FILE_SIZE", 4*1024*1024*1024),                     // 4GB
		RetryPeriod:  time.Duration(getEnvIntOrDefault("DIRWATCHER_RETRY_PERIOD", 300)) * time.Second,        // 5 minutes default
		SettlingTime: time.Duration(getEnvIntOrDefault("DIRWATCHER_SETTLING_TIME", 1000)) * time.Millisecond, // 1 second default
	}

	// Validate configuration values
	if err := config.validate(); err != nil {
		return nil, fmt.Errorf("invalid configuration: %w", err)
	}

	return config, nil
}

// validate checks that configuration values are reasonable
func (c *Config) validate() error {
	if c.URL == "" {
		return fmt.Errorf("DELTAFI_URL environment variable is required")
	}

	if c.Workers <= 0 {
		return fmt.Errorf("DIRWATCHER_WORKERS must be greater than 0, got %d", c.Workers)
	}

	if c.MaxFileSize <= 0 {
		return fmt.Errorf("DIRWATCHER_MAX_FILE_SIZE must be greater than 0, got %d", c.MaxFileSize)
	}

	if c.RetryPeriod <= 0 {
		return fmt.Errorf("DIRWATCHER_RETRY_PERIOD must be greater than 0, got %d", c.RetryPeriod)
	}

	if c.SettlingTime < 100 {
		return fmt.Errorf("DIRWATCHER_SETTLING_TIME must be at least 100ms, got %d", c.SettlingTime)
	}

	if c.WatchDir == "" {
		return fmt.Errorf("DIRWATCHER_WATCH_DIR cannot be empty")
	}

	return nil
}

func getEnvOrDefault(key, defaultValue string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return defaultValue
}

func getEnvIntOrDefault(key string, defaultValue int) int {
	strValue, exists := os.LookupEnv(key)
	if !exists {
		return defaultValue
	}

	value, err := strconv.Atoi(strValue)
	if err != nil {
		return defaultValue
	}
	return value
}

func getEnvInt64OrDefault(key string, defaultValue int64) int64 {
	strValue, exists := os.LookupEnv(key)
	if !exists {
		return defaultValue
	}

	value, err := strconv.ParseInt(strValue, 10, 64)
	if err != nil {
		return defaultValue
	}
	return value
}
