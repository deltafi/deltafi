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
package transport

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"go.uber.org/zap"
)

func TestHTTPClient_HandleFile(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create a test file
	testFile := filepath.Join(tmpDir, "test.txt")
	testContent := "test content"
	if err := os.WriteFile(testFile, []byte(testContent), 0644); err != nil {
		t.Fatalf("Failed to create test file: %v", err)
	}

	tests := []struct {
		name          string
		setupServer   func() *httptest.Server
		filePath      string
		metadata      map[string]string
		expectedError bool
	}{
		{
			name: "successful upload",
			setupServer: func() *httptest.Server {
				return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					// Verify headers
					if r.Header.Get("Content-Type") != "application/octet-stream" {
						t.Errorf("Expected Content-Type header to be application/octet-stream, got %s", r.Header.Get("Content-Type"))
					}
					if r.Header.Get("filename") != "test.txt" {
						t.Errorf("Expected filename header to be test.txt, got %s", r.Header.Get("filename"))
					}
					if r.Header.Get("dataSource") != "testSource" {
						t.Errorf("Expected dataSource header to be testSource, got %s", r.Header.Get("dataSource"))
					}
					if r.Header.Get("metadata") != `{"environment":"test"}` {
						t.Errorf("Expected metadata header to be {\"environment\":\"test\"}, got %s", r.Header.Get("metadata"))
					}

					// Read and verify body
					body, err := io.ReadAll(r.Body)
					if err != nil {
						t.Errorf("Failed to read request body: %v", err)
					}
					if string(body) != testContent {
						t.Errorf("Expected body to be %q, got %q", testContent, string(body))
					}

					w.WriteHeader(http.StatusOK)
				}))
			},
			filePath: testFile,
			metadata: map[string]string{
				"dataSource": "testSource",
				"metadata":   `{"environment":"test"}`,
			},
			expectedError: false,
		},
		{
			name: "server error",
			setupServer: func() *httptest.Server {
				return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					w.WriteHeader(http.StatusInternalServerError)
					fmt.Fprintln(w, "internal server error")
				}))
			},
			filePath: testFile,
			metadata: map[string]string{
				"dataSource": "testSource",
			},
			expectedError: true,
		},
		{
			name: "non-existent file",
			setupServer: func() *httptest.Server {
				return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					w.WriteHeader(http.StatusOK)
				}))
			},
			filePath:      filepath.Join(tmpDir, "nonexistent.txt"),
			metadata:      map[string]string{},
			expectedError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := tt.setupServer()
			defer server.Close()

			client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)
			err := client.HandleFile(context.Background(), tt.filePath, tt.metadata)

			if tt.expectedError && err == nil {
				t.Error("Expected an error but got none")
			}
			if !tt.expectedError && err != nil {
				t.Errorf("Unexpected error: %v", err)
			}
		})
	}
}

func TestHTTPClient_RetryMechanism(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	tests := []struct {
		name           string
		setupServer    func() *httptest.Server
		deleteFile     bool
		expectRetry    bool
		expectInQueue  bool
		expectUpload   bool
		expectFileGone bool
	}{
		{
			name: "successful retry",
			setupServer: func() *httptest.Server {
				failFirst := true
				return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					if failFirst {
						failFirst = false
						w.WriteHeader(http.StatusInternalServerError)
						return
					}
					w.WriteHeader(http.StatusOK)
				}))
			},
			deleteFile:     false,
			expectRetry:    true,
			expectInQueue:  false,
			expectUpload:   true,
			expectFileGone: true,
		},
		{
			name: "file deleted before retry",
			setupServer: func() *httptest.Server {
				return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					w.WriteHeader(http.StatusInternalServerError)
				}))
			},
			deleteFile:     true,
			expectRetry:    false,
			expectInQueue:  false,
			expectUpload:   false,
			expectFileGone: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create a test file
			testFile := filepath.Join(tmpDir, "retry_test.txt")
			testContent := "test content"
			if err := os.WriteFile(testFile, []byte(testContent), 0644); err != nil {
				t.Fatalf("Failed to create test file: %v", err)
			}

			server := tt.setupServer()
			defer server.Close()

			metadata := map[string]string{
				"dataSource": "testSource",
			}

			client := NewHTTPClient(server.URL, 5*time.Second, 100*time.Millisecond, logger)
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			// Start retry worker
			go client.StartRetryWorker(ctx)

			// Initial attempt should fail
			err = client.HandleFile(ctx, testFile, metadata)
			if err == nil {
				t.Fatal("Expected first attempt to fail")
			}

			// Verify file is in failed files map
			if _, exists := client.failedFiles.Load(testFile); !exists {
				t.Error("Expected file to be in failed files map")
			}

			if tt.deleteFile {
				// Delete the file before retry
				if err := os.Remove(testFile); err != nil {
					t.Fatalf("Failed to delete test file: %v", err)
				}
			}

			// Wait for retry attempt
			deadline := time.Now().Add(2 * time.Second)
			var inQueue bool
			for time.Now().Before(deadline) {
				if _, exists := client.failedFiles.Load(testFile); !exists {
					inQueue = false
					break
				}
				inQueue = true
				time.Sleep(100 * time.Millisecond)
			}

			if inQueue != tt.expectInQueue {
				t.Errorf("Expected file in queue: %v, got: %v", tt.expectInQueue, inQueue)
			}

			// Check if file exists
			_, err = os.Stat(testFile)
			fileExists := !os.IsNotExist(err)
			if fileExists == tt.expectFileGone {
				t.Errorf("Expected file gone: %v, got: file exists: %v", tt.expectFileGone, fileExists)
			}
		})
	}
}

func TestHTTPClient_Context(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create a test file
	testFile := filepath.Join(tmpDir, "test.txt")
	if err := os.WriteFile(testFile, []byte("test content"), 0644); err != nil {
		t.Fatalf("Failed to create test file: %v", err)
	}

	// Create a server that delays before responding
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(2 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)

	// Create a context that times out quickly
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()

	err = client.HandleFile(ctx, testFile, nil)
	if err == nil {
		t.Error("Expected timeout error, got nil")
		return
	}

	// Check if the error or any wrapped error is context.DeadlineExceeded
	if !strings.Contains(err.Error(), context.DeadlineExceeded.Error()) {
		t.Errorf("Expected error to contain %q, got: %v", context.DeadlineExceeded, err)
	}
}
