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
	"os"
	"strings"
	"testing"
)

func TestLoadConfig(t *testing.T) {
	tests := []struct {
		name        string
		envVars     map[string]string
		want        *Config
		wantErr     bool
		errContains string
	}{
		{
			name: "default values",
			want: &Config{
				WatchDir:    "watched-dir",
				URL:         "http://deltafi-core-service",
				IngressAPI:  "/api/v2/deltafile/ingress",
				Endpoint:    "http://deltafi-core-service/api/v2/deltafile/ingress",
				Workers:     5,
				BufferSize:  32 * 1024 * 1024,
				MaxFileSize: 2 * 1024 * 1024 * 1024,
				RetryPeriod: 300,
			},
		},
		{
			name: "custom values",
			envVars: map[string]string{
				"DELTAFI_WATCH_DIR":     "/custom/dir",
				"DELTAFI_URL":           "http://custom-core:8080",
				"DELTAFI_INGRESS_API":   "/custom/ingress",
				"DELTAFI_WORKERS":       "10",
				"DELTAFI_BUFFER_SIZE":   "2097152",
				"DELTAFI_MAX_FILE_SIZE": "209715200",
				"DELTAFI_RETRY_PERIOD":  "60",
			},
			want: &Config{
				WatchDir:    "/custom/dir",
				URL:         "http://custom-core:8080",
				IngressAPI:  "/custom/ingress",
				Endpoint:    "http://custom-core:8080/custom/ingress",
				Workers:     10,
				BufferSize:  2097152,
				MaxFileSize: 209715200,
				RetryPeriod: 60,
			},
		},
		{
			name: "invalid worker count",
			envVars: map[string]string{
				"DELTAFI_WORKERS": "invalid",
			},
			want: &Config{
				WatchDir:    "watched-dir",
				URL:         "http://deltafi-core-service",
				IngressAPI:  "/api/v2/deltafile/ingress",
				Endpoint:    "http://deltafi-core-service/api/v2/deltafile/ingress",
				Workers:     5, // Should use default
				BufferSize:  32 * 1024 * 1024,
				MaxFileSize: 2 * 1024 * 1024 * 1024,
				RetryPeriod: 300,
			},
		},
		{
			name: "invalid buffer size",
			envVars: map[string]string{
				"DELTAFI_BUFFER_SIZE": "invalid",
			},
			want: &Config{
				WatchDir:    "watched-dir",
				URL:         "http://deltafi-core-service",
				IngressAPI:  "/api/v2/deltafile/ingress",
				Endpoint:    "http://deltafi-core-service/api/v2/deltafile/ingress",
				Workers:     5,
				BufferSize:  32 * 1024 * 1024, // Should use default
				MaxFileSize: 2 * 1024 * 1024 * 1024,
				RetryPeriod: 300,
			},
		},
		{
			name: "invalid max file size",
			envVars: map[string]string{
				"DELTAFI_MAX_FILE_SIZE": "invalid",
			},
			want: &Config{
				WatchDir:    "watched-dir",
				URL:         "http://deltafi-core-service",
				IngressAPI:  "/api/v2/deltafile/ingress",
				Endpoint:    "http://deltafi-core-service/api/v2/deltafile/ingress",
				Workers:     5,
				BufferSize:  32 * 1024 * 1024,
				MaxFileSize: 2 * 1024 * 1024 * 1024, // Should use default
				RetryPeriod: 300,
			},
		},
		{
			name: "invalid retry period",
			envVars: map[string]string{
				"DELTAFI_RETRY_PERIOD": "invalid",
			},
			want: &Config{
				WatchDir:    "watched-dir",
				URL:         "http://deltafi-core-service",
				IngressAPI:  "/api/v2/deltafile/ingress",
				Endpoint:    "http://deltafi-core-service/api/v2/deltafile/ingress",
				Workers:     5,
				BufferSize:  32 * 1024 * 1024,
				MaxFileSize: 2 * 1024 * 1024 * 1024,
				RetryPeriod: 300, // Should use default
			},
		},
		{
			name: "empty URL",
			envVars: map[string]string{
				"DELTAFI_URL": "",
			},
			wantErr:     true,
			errContains: "DELTAFI_URL environment variable is required",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Clear environment before each test
			os.Clearenv()

			// Set environment variables for the test
			for k, v := range tt.envVars {
				os.Setenv(k, v)
			}

			got, err := LoadConfig()
			if (err != nil) != tt.wantErr {
				t.Errorf("LoadConfig() error = %v, wantErr %v", err, tt.wantErr)
				return
			}

			if tt.wantErr {
				if err == nil || !contains(err.Error(), tt.errContains) {
					t.Errorf("LoadConfig() error = %v, want error containing %q", err, tt.errContains)
				}
				return
			}

			if got.WatchDir != tt.want.WatchDir {
				t.Errorf("WatchDir = %v, want %v", got.WatchDir, tt.want.WatchDir)
			}
			if got.URL != tt.want.URL {
				t.Errorf("URL = %v, want %v", got.URL, tt.want.URL)
			}
			if got.IngressAPI != tt.want.IngressAPI {
				t.Errorf("IngressAPI = %v, want %v", got.IngressAPI, tt.want.IngressAPI)
			}
			if got.Endpoint != tt.want.Endpoint {
				t.Errorf("Endpoint = %v, want %v", got.Endpoint, tt.want.Endpoint)
			}
			if got.Workers != tt.want.Workers {
				t.Errorf("Workers = %v, want %v", got.Workers, tt.want.Workers)
			}
			if got.BufferSize != tt.want.BufferSize {
				t.Errorf("BufferSize = %v, want %v", got.BufferSize, tt.want.BufferSize)
			}
			if got.MaxFileSize != tt.want.MaxFileSize {
				t.Errorf("MaxFileSize = %v, want %v", got.MaxFileSize, tt.want.MaxFileSize)
			}
			if got.RetryPeriod != tt.want.RetryPeriod {
				t.Errorf("RetryPeriod = %v, want %v", got.RetryPeriod, tt.want.RetryPeriod)
			}
		})
	}
}

func contains(s, substr string) bool {
	return strings.Contains(s, substr)
}
