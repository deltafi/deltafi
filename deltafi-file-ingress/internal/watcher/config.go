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
)

// Config holds all configuration for the file ingress service
type Config struct {
	WatchDir    string
	URL         string
	IngressAPI  string
	Endpoint    string // Computed from URL and IngressAPI
	Workers     int
	BufferSize  int
	MaxFileSize int64
	RetryPeriod int // Retry period in seconds
}

// LoadConfig loads the configuration from environment variables
func LoadConfig() (*Config, error) {
	url := getEnvOrDefault("DELTAFI_URL", "http://deltafi-core-service")
	ingressAPI := getEnvOrDefault("DELTAFI_INGRESS_API", "/api/v2/deltafile/ingress")

	config := &Config{
		// Default values
		WatchDir:    getEnvOrDefault("DELTAFI_WATCH_DIR", "watched-dir"),
		URL:         url,
		IngressAPI:  ingressAPI,
		Endpoint:    url + ingressAPI,
		Workers:     getEnvIntOrDefault("DELTAFI_WORKERS", 5),
		BufferSize:  getEnvIntOrDefault("DELTAFI_BUFFER_SIZE", 32*1024*1024),         // 32MB
		MaxFileSize: getEnvInt64OrDefault("DELTAFI_MAX_FILE_SIZE", 2*1024*1024*1024), // 2GB
		RetryPeriod: getEnvIntOrDefault("DELTAFI_RETRY_PERIOD", 300),                 // 5 minutes default
	}

	// Validate required fields
	if config.URL == "" {
		return nil, fmt.Errorf("DELTAFI_URL environment variable is required")
	}

	return config, nil
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
