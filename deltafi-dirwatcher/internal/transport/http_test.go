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

func TestHTTPClient_FailedFilesTracking(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	t.Run("failed file is added to retry map", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "fail_track.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)
		metadata := map[string]string{"key": "value"}

		err := client.HandleFile(context.Background(), testFile, metadata)
		if err == nil {
			t.Fatal("Expected error, got nil")
		}

		// Verify file is tracked
		value, exists := client.failedFiles.Load(testFile)
		if !exists {
			t.Fatal("Expected file to be in failed files map")
		}

		failedFile := value.(*FailedFile)
		if failedFile.Path != testFile {
			t.Errorf("FailedFile.Path = %q, want %q", failedFile.Path, testFile)
		}
		if failedFile.Metadata["key"] != "value" {
			t.Errorf("FailedFile.Metadata[key] = %q, want %q", failedFile.Metadata["key"], "value")
		}
		if failedFile.RetryCount != 0 {
			t.Errorf("FailedFile.RetryCount = %d, want 0", failedFile.RetryCount)
		}
	})

	t.Run("successful upload removes file from retry map", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "success_remove.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		attemptCount := 0
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			attemptCount++
			if attemptCount == 1 {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			w.WriteHeader(http.StatusOK)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 5*time.Second, 50*time.Millisecond, logger)
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()

		// Start retry worker
		go client.StartRetryWorker(ctx)

		// First attempt fails
		err := client.HandleFile(ctx, testFile, nil)
		if err == nil {
			t.Fatal("Expected first attempt to fail")
		}

		// Wait for retry
		time.Sleep(200 * time.Millisecond)

		// File should be removed from map after successful retry
		if _, exists := client.failedFiles.Load(testFile); exists {
			t.Error("Expected file to be removed from failed files map after successful retry")
		}
	})

	t.Run("retry increments retry count", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "retry_count.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 5*time.Second, 50*time.Millisecond, logger)
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()

		// Start retry worker
		go client.StartRetryWorker(ctx)

		// Initial failure
		client.HandleFile(ctx, testFile, nil)

		// Wait for at least one retry
		time.Sleep(200 * time.Millisecond)

		value, exists := client.failedFiles.Load(testFile)
		if !exists {
			t.Fatal("Expected file to still be in failed files map")
		}

		failedFile := value.(*FailedFile)
		if failedFile.RetryCount < 1 {
			t.Errorf("Expected RetryCount >= 1, got %d", failedFile.RetryCount)
		}
	})
}

func TestHTTPClient_Headers(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	t.Run("required headers are set", func(t *testing.T) {
		// Create a fresh file for this test
		testFile := filepath.Join(tmpDir, "headers_test1.txt")
		if err := os.WriteFile(testFile, []byte("test content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		var receivedHeaders http.Header

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			receivedHeaders = r.Header.Clone()
			w.WriteHeader(http.StatusOK)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)
		client.HandleFile(context.Background(), testFile, nil)

		expectedHeaders := map[string]string{
			"Content-Type":       "application/octet-stream",
			"Filename":           "headers_test1.txt",
			"X-User-Permissions": "Admin",
			"X-User-Name":        "deltafi-cli",
		}

		for key, expected := range expectedHeaders {
			if got := receivedHeaders.Get(key); got != expected {
				t.Errorf("Header %q = %q, want %q", key, got, expected)
			}
		}
	})

	t.Run("metadata is passed as headers", func(t *testing.T) {
		// Create a fresh file for this test
		testFile := filepath.Join(tmpDir, "headers_test2.txt")
		if err := os.WriteFile(testFile, []byte("test content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		var receivedHeaders http.Header

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			receivedHeaders = r.Header.Clone()
			w.WriteHeader(http.StatusOK)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)
		metadata := map[string]string{
			"dataSource": "test-source",
			"metadata":   `{"env":"test"}`,
		}
		client.HandleFile(context.Background(), testFile, metadata)

		if got := receivedHeaders.Get("dataSource"); got != "test-source" {
			t.Errorf("Header dataSource = %q, want %q", got, "test-source")
		}
		if got := receivedHeaders.Get("metadata"); got != `{"env":"test"}` {
			t.Errorf("Header metadata = %q, want %q", got, `{"env":"test"}`)
		}
	})
}

func TestHTTPClient_LargeFile(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	t.Run("streams large file correctly", func(t *testing.T) {
		// Create a 1MB file
		testFile := filepath.Join(tmpDir, "large_file.bin")
		largeContent := make([]byte, 1024*1024) // 1MB
		for i := range largeContent {
			largeContent[i] = byte(i % 256)
		}
		if err := os.WriteFile(testFile, largeContent, 0644); err != nil {
			t.Fatalf("Failed to create large file: %v", err)
		}

		var receivedBytes int64
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			body, err := io.ReadAll(r.Body)
			if err != nil {
				t.Errorf("Failed to read body: %v", err)
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			receivedBytes = int64(len(body))
			w.WriteHeader(http.StatusOK)
		}))
		defer server.Close()

		client := NewHTTPClient(server.URL, 30*time.Second, 60*time.Second, logger)
		err := client.HandleFile(context.Background(), testFile, nil)
		if err != nil {
			t.Fatalf("HandleFile() error = %v", err)
		}

		if receivedBytes != int64(len(largeContent)) {
			t.Errorf("Received %d bytes, want %d bytes", receivedBytes, len(largeContent))
		}
	})
}

func TestHTTPClient_ResponseCodes(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "http_client_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	statusCodes := []struct {
		code        int
		shouldError bool
	}{
		{http.StatusOK, false},
		{http.StatusCreated, true},  // Only 200 is accepted
		{http.StatusAccepted, true}, // Only 200 is accepted
		{http.StatusBadRequest, true},
		{http.StatusUnauthorized, true},
		{http.StatusForbidden, true},
		{http.StatusNotFound, true},
		{http.StatusInternalServerError, true},
		{http.StatusBadGateway, true},
		{http.StatusServiceUnavailable, true},
	}

	for _, tc := range statusCodes {
		t.Run(fmt.Sprintf("status_%d", tc.code), func(t *testing.T) {
			testFile := filepath.Join(tmpDir, fmt.Sprintf("status_%d.txt", tc.code))
			if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
				t.Fatalf("Failed to create test file: %v", err)
			}

			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.code)
			}))
			defer server.Close()

			client := NewHTTPClient(server.URL, 5*time.Second, 60*time.Second, logger)
			err := client.HandleFile(context.Background(), testFile, nil)

			if tc.shouldError && err == nil {
				t.Errorf("Expected error for status %d, got nil", tc.code)
			}
			if !tc.shouldError && err != nil {
				t.Errorf("Unexpected error for status %d: %v", tc.code, err)
			}
		})
	}
}

func TestHTTPClient_RetryWorkerContextCancellation(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	client := NewHTTPClient("http://example.com", 5*time.Second, 100*time.Millisecond, logger)

	ctx, cancel := context.WithCancel(context.Background())

	done := make(chan struct{})
	go func() {
		client.StartRetryWorker(ctx)
		close(done)
	}()

	// Let worker run briefly
	time.Sleep(50 * time.Millisecond)

	// Cancel context
	cancel()

	// Worker should stop
	select {
	case <-done:
		// Success - worker stopped
	case <-time.After(500 * time.Millisecond):
		t.Error("Retry worker did not stop after context cancellation")
	}
}
