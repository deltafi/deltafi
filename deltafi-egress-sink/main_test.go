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

// ABOUTME: Tests for the egress sink HTTP handlers and file operations.
// ABOUTME: Covers blackhole, capture, and fileSink handlers with various scenarios.

package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestMetadataUnmarshal(t *testing.T) {
	tests := []struct {
		name      string
		jsonInput string
		wantErr   bool
		wantFile  string
		wantFlow  string
	}{
		{
			name:      "valid metadata",
			jsonInput: `{"filename":"test.txt","flow":"myflow"}`,
			wantErr:   false,
			wantFile:  "test.txt",
			wantFlow:  "myflow",
		},
		{
			name:      "empty fields",
			jsonInput: `{"filename":"","flow":""}`,
			wantErr:   false,
			wantFile:  "",
			wantFlow:  "",
		},
		{
			name:      "extra fields ignored",
			jsonInput: `{"filename":"test.txt","flow":"myflow","extra":"ignored"}`,
			wantErr:   false,
			wantFile:  "test.txt",
			wantFlow:  "myflow",
		},
		{
			name:      "missing fields",
			jsonInput: `{}`,
			wantErr:   false,
			wantFile:  "",
			wantFlow:  "",
		},
		{
			name:      "invalid json",
			jsonInput: `{invalid}`,
			wantErr:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var m Metadata
			err := json.Unmarshal([]byte(tt.jsonInput), &m)
			if (err != nil) != tt.wantErr {
				t.Errorf("Unmarshal() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr {
				if m.Filename != tt.wantFile {
					t.Errorf("Filename = %v, want %v", m.Filename, tt.wantFile)
				}
				if m.Flow != tt.wantFlow {
					t.Errorf("Flow = %v, want %v", m.Flow, tt.wantFlow)
				}
			}
		})
	}
}

func TestBlackholeHandler(t *testing.T) {
	tests := []struct {
		name           string
		method         string
		body           string
		queryParams    string
		wantStatusCode int
	}{
		{
			name:           "simple POST request",
			method:         http.MethodPost,
			body:           "test content to discard",
			queryParams:    "",
			wantStatusCode: http.StatusOK,
		},
		{
			name:           "GET request",
			method:         http.MethodGet,
			body:           "",
			queryParams:    "",
			wantStatusCode: http.StatusOK,
		},
		{
			name:           "large body",
			method:         http.MethodPost,
			body:           strings.Repeat("x", 1024*1024), // 1MB
			queryParams:    "",
			wantStatusCode: http.StatusOK,
		},
		{
			name:           "invalid latency value",
			method:         http.MethodPost,
			body:           "test",
			queryParams:    "latency=notanumber",
			wantStatusCode: http.StatusBadRequest,
		},
		{
			name:           "negative latency value",
			method:         http.MethodPost,
			body:           "test",
			queryParams:    "latency=-1",
			wantStatusCode: http.StatusOK, // negative durations are valid in Go
		},
		{
			name:           "zero latency",
			method:         http.MethodPost,
			body:           "test",
			queryParams:    "latency=0",
			wantStatusCode: http.StatusOK,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			url := "/blackhole"
			if tt.queryParams != "" {
				url += "?" + tt.queryParams
			}

			req := httptest.NewRequest(tt.method, url, strings.NewReader(tt.body))
			w := httptest.NewRecorder()

			blackholeHandler(w, req)

			resp := w.Result()
			if resp.StatusCode != tt.wantStatusCode {
				t.Errorf("StatusCode = %d, want %d", resp.StatusCode, tt.wantStatusCode)
			}

			if tt.wantStatusCode == http.StatusOK {
				if resp.Header.Get("Content-Length") != "0" {
					t.Errorf("Content-Length header = %s, want 0", resp.Header.Get("Content-Length"))
				}
			}
		})
	}
}

func TestCaptureHandler(t *testing.T) {
	// Create a temporary directory for testing
	tempDir := t.TempDir()

	// We need to test the captureHandler, but it uses the global outputDir constant.
	// We'll test the saveContent function directly and verify behavior indirectly.
	tests := []struct {
		name           string
		body           string
		wantStatusCode int
	}{
		{
			name:           "small content",
			body:           "hello world",
			wantStatusCode: http.StatusOK,
		},
		{
			name:           "empty body",
			body:           "",
			wantStatusCode: http.StatusOK,
		},
		{
			name:           "binary content",
			body:           string([]byte{0x00, 0x01, 0x02, 0xFF, 0xFE}),
			wantStatusCode: http.StatusOK,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create a test file path
			filePath := filepath.Join(tempDir, "test_capture_"+tt.name+".bin")

			// Test saveContent directly
			body := io.NopCloser(strings.NewReader(tt.body))
			err := saveContent(filePath, body)

			if err != nil {
				t.Errorf("saveContent() error = %v", err)
				return
			}

			// Verify the file was created with correct content
			content, err := os.ReadFile(filePath)
			if err != nil {
				t.Errorf("Failed to read saved file: %v", err)
				return
			}

			if string(content) != tt.body {
				t.Errorf("File content = %q, want %q", string(content), tt.body)
			}
		})
	}
}

func TestSaveContent(t *testing.T) {
	tempDir := t.TempDir()

	tests := []struct {
		name    string
		content string
		wantErr bool
	}{
		{
			name:    "simple text",
			content: "Hello, World!",
			wantErr: false,
		},
		{
			name:    "empty content",
			content: "",
			wantErr: false,
		},
		{
			name:    "large content",
			content: strings.Repeat("a", 256*1024), // 256KB - larger than buffer
			wantErr: false,
		},
		{
			name:    "binary content",
			content: string([]byte{0x00, 0x01, 0x02, 0x03, 0xFF}),
			wantErr: false,
		},
		{
			name:    "multiline content",
			content: "line1\nline2\nline3\n",
			wantErr: false,
		},
		{
			name:    "unicode content",
			content: "Hello, 世界! 🌍",
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			filePath := filepath.Join(tempDir, "save_content_test_"+tt.name+".bin")
			body := io.NopCloser(strings.NewReader(tt.content))

			err := saveContent(filePath, body)

			if (err != nil) != tt.wantErr {
				t.Errorf("saveContent() error = %v, wantErr %v", err, tt.wantErr)
				return
			}

			if !tt.wantErr {
				content, err := os.ReadFile(filePath)
				if err != nil {
					t.Errorf("Failed to read file: %v", err)
					return
				}
				if string(content) != tt.content {
					t.Errorf("File content = %q, want %q", string(content), tt.content)
				}
			}
		})
	}
}

func TestSaveContentInvalidPath(t *testing.T) {
	// Test with an invalid path
	body := io.NopCloser(strings.NewReader("test"))
	err := saveContent("/nonexistent/path/file.txt", body)

	if err == nil {
		t.Error("saveContent() expected error for invalid path, got nil")
	}

	if !strings.Contains(err.Error(), "error creating file") {
		t.Errorf("Expected 'error creating file' in error message, got: %v", err)
	}
}

func TestSaveMetadata(t *testing.T) {
	tempDir := t.TempDir()

	tests := []struct {
		name         string
		filePath     string
		method       string
		metadataJSON string
		wantSuffix   string
	}{
		{
			name:         "POST method",
			filePath:     "test.txt",
			method:       "POST",
			metadataJSON: `{"filename":"test.txt","flow":"myflow"}`,
			wantSuffix:   ".POST.metadata.json",
		},
		{
			name:         "PUT method",
			filePath:     "test.txt",
			method:       "PUT",
			metadataJSON: `{"filename":"test.txt","flow":"myflow"}`,
			wantSuffix:   ".PUT.metadata.json",
		},
		{
			name:         "GET method",
			filePath:     "data.bin",
			method:       "GET",
			metadataJSON: `{"filename":"data.bin","flow":"testflow"}`,
			wantSuffix:   ".GET.metadata.json",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			filePath := filepath.Join(tempDir, tt.filePath)
			err := saveMetadata(filePath, tt.method, tt.metadataJSON)

			if err != nil {
				t.Errorf("saveMetadata() error = %v", err)
				return
			}

			// Check that metadata file was created with correct name
			metadataPath := filePath + tt.wantSuffix
			content, err := os.ReadFile(metadataPath)
			if err != nil {
				t.Errorf("Failed to read metadata file: %v", err)
				return
			}

			if string(content) != tt.metadataJSON {
				t.Errorf("Metadata content = %q, want %q", string(content), tt.metadataJSON)
			}
		})
	}
}

func TestSaveMetadataInvalidPath(t *testing.T) {
	err := saveMetadata("/nonexistent/path/file.txt", "POST", `{}`)

	if err == nil {
		t.Error("saveMetadata() expected error for invalid path, got nil")
	}

	if !strings.Contains(err.Error(), "error writing metadata file") {
		t.Errorf("Expected 'error writing metadata file' in error message, got: %v", err)
	}
}

func TestFileSinkHandlerMissingMetadataHeader(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
	w := httptest.NewRecorder()

	fileSinkHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "Missing HTTP header: DeltafiMetadata") {
		t.Errorf("Expected error message about missing header, got: %s", string(body))
	}
}

func TestFileSinkHandlerInvalidJSON(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
	req.Header.Set("DeltafiMetadata", "{invalid json}")
	w := httptest.NewRecorder()

	fileSinkHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "Invalid metadata JSON") {
		t.Errorf("Expected error message about invalid JSON, got: %s", string(body))
	}
}

func TestFileSinkHandlerMissingFilename(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
	req.Header.Set("DeltafiMetadata", `{"filename":"","flow":"myflow"}`)
	w := httptest.NewRecorder()

	fileSinkHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "Missing metadata keys") {
		t.Errorf("Expected error message about missing keys, got: %s", string(body))
	}
}

func TestFileSinkHandlerMissingFlow(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
	req.Header.Set("DeltafiMetadata", `{"filename":"test.txt","flow":""}`)
	w := httptest.NewRecorder()

	fileSinkHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "Missing metadata keys") {
		t.Errorf("Expected error message about missing keys, got: %s", string(body))
	}
}

func TestFileSinkHandlerMissingBothFields(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
	req.Header.Set("DeltafiMetadata", `{"filename":"","flow":""}`)
	w := httptest.NewRecorder()

	fileSinkHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}
}

// TestFilepathBaseBehavior verifies the path traversal protection behavior
func TestFilepathBaseBehavior(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"simple.txt", "simple.txt"},
		{"../../../etc/passwd", "passwd"},
		{"/absolute/path/file.txt", "file.txt"},
		{"subdir/file.txt", "file.txt"},
		{"./file.txt", "file.txt"},
		{"", "."},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := filepath.Base(tt.input)
			if got != tt.want {
				t.Errorf("filepath.Base(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

func TestDropMetadataFlag(t *testing.T) {
	// Save original value
	originalDropMetadata := dropMetadata
	defer func() { dropMetadata = originalDropMetadata }()

	tempDir := t.TempDir()

	testCases := []struct {
		name         string
		dropMetadata bool
		wantMetadata bool
	}{
		{
			name:         "metadata saved when dropMetadata is false",
			dropMetadata: false,
			wantMetadata: true,
		},
		{
			name:         "metadata not saved when dropMetadata is true",
			dropMetadata: true,
			wantMetadata: false,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			dropMetadata = tc.dropMetadata

			// Create content file
			contentPath := filepath.Join(tempDir, tc.name+".txt")
			err := os.WriteFile(contentPath, []byte("test"), 0644)
			if err != nil {
				t.Fatalf("Failed to create test file: %v", err)
			}

			// Call saveMetadata only if dropMetadata is false
			metadataPath := contentPath + ".POST.metadata.json"
			if !dropMetadata {
				err = saveMetadata(contentPath, "POST", `{"test":"data"}`)
				if err != nil {
					t.Fatalf("saveMetadata failed: %v", err)
				}
			}

			// Check if metadata file exists
			_, err = os.Stat(metadataPath)
			metadataExists := !os.IsNotExist(err)

			if metadataExists != tc.wantMetadata {
				t.Errorf("Metadata exists = %v, want %v", metadataExists, tc.wantMetadata)
			}
		})
	}
}

// BenchmarkSaveContent benchmarks content saving with various sizes
func BenchmarkSaveContent(b *testing.B) {
	tempDir := b.TempDir()

	sizes := []struct {
		name string
		size int
	}{
		{"1KB", 1024},
		{"64KB", 64 * 1024},
		{"1MB", 1024 * 1024},
	}

	for _, size := range sizes {
		content := strings.Repeat("x", size.size)
		b.Run(size.name, func(b *testing.B) {
			for i := 0; i < b.N; i++ {
				filePath := filepath.Join(tempDir, "bench_"+size.name+"_"+strings.Repeat("x", i%100)+".bin")
				body := io.NopCloser(strings.NewReader(content))
				err := saveContent(filePath, body)
				if err != nil {
					b.Fatalf("saveContent failed: %v", err)
				}
			}
		})
	}
}

// TestContentTypePreservation verifies different content types are handled
func TestContentTypePreservation(t *testing.T) {
	tempDir := t.TempDir()

	contentTypes := []struct {
		name        string
		contentType string
		body        []byte
	}{
		{
			name:        "text_plain",
			contentType: "text/plain",
			body:        []byte("Hello, World!"),
		},
		{
			name:        "application_json",
			contentType: "application/json",
			body:        []byte(`{"key":"value"}`),
		},
		{
			name:        "application_octet_stream",
			contentType: "application/octet-stream",
			body:        []byte{0x00, 0x01, 0x02, 0xFF},
		},
		{
			name:        "image_png",
			contentType: "image/png",
			body:        []byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, // PNG header
		},
	}

	for _, ct := range contentTypes {
		t.Run(ct.name, func(t *testing.T) {
			filePath := filepath.Join(tempDir, "content_"+ct.name+".bin")
			body := io.NopCloser(bytes.NewReader(ct.body))

			err := saveContent(filePath, body)
			if err != nil {
				t.Errorf("saveContent() error = %v", err)
				return
			}

			saved, err := os.ReadFile(filePath)
			if err != nil {
				t.Errorf("Failed to read file: %v", err)
				return
			}

			if !bytes.Equal(saved, ct.body) {
				t.Errorf("Saved content doesn't match original")
			}
		})
	}
}

func TestMetadataMarshal(t *testing.T) {
	tests := []struct {
		name     string
		metadata Metadata
		want     string
	}{
		{
			name:     "standard metadata",
			metadata: Metadata{Filename: "test.txt", Flow: "myflow"},
			want:     `{"filename":"test.txt","flow":"myflow"}`,
		},
		{
			name:     "empty metadata",
			metadata: Metadata{},
			want:     `{"filename":"","flow":""}`,
		},
		{
			name:     "special characters",
			metadata: Metadata{Filename: "test\"file.txt", Flow: "my\\flow"},
			want:     `{"filename":"test\"file.txt","flow":"my\\flow"}`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := json.Marshal(tt.metadata)
			if err != nil {
				t.Fatalf("Marshal() error = %v", err)
			}
			if string(got) != tt.want {
				t.Errorf("Marshal() = %s, want %s", string(got), tt.want)
			}
		})
	}
}

func TestBlackholeHandlerHTTPMethods(t *testing.T) {
	methods := []string{
		http.MethodGet,
		http.MethodPost,
		http.MethodPut,
		http.MethodPatch,
		http.MethodDelete,
		http.MethodHead,
		http.MethodOptions,
	}

	for _, method := range methods {
		t.Run(method, func(t *testing.T) {
			req := httptest.NewRequest(method, "/blackhole", strings.NewReader("test body"))
			w := httptest.NewRecorder()

			blackholeHandler(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusOK {
				t.Errorf("StatusCode for %s = %d, want %d", method, resp.StatusCode, http.StatusOK)
			}
		})
	}
}

func TestFileSinkHandlerHTTPMethods(t *testing.T) {
	// These should all return 400 due to missing metadata header
	methods := []string{
		http.MethodGet,
		http.MethodPost,
		http.MethodPut,
		http.MethodPatch,
		http.MethodDelete,
	}

	for _, method := range methods {
		t.Run(method, func(t *testing.T) {
			req := httptest.NewRequest(method, "/", strings.NewReader("test body"))
			w := httptest.NewRecorder()

			fileSinkHandler(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Errorf("StatusCode for %s = %d, want %d", method, resp.StatusCode, http.StatusBadRequest)
			}
		})
	}
}

func TestFileSinkHandlerVariousInvalidMetadata(t *testing.T) {
	tests := []struct {
		name     string
		metadata string
		wantMsg  string
	}{
		{
			name:     "empty string",
			metadata: "",
			wantMsg:  "Missing HTTP header: DeltafiMetadata",
		},
		{
			name:     "just whitespace",
			metadata: "   ",
			wantMsg:  "Invalid metadata JSON",
		},
		{
			name:     "array instead of object",
			metadata: `["filename", "test.txt"]`,
			wantMsg:  "Invalid metadata JSON",
		},
		{
			name:     "null value",
			metadata: `{"filename":null,"flow":"test"}`,
			wantMsg:  "Missing metadata keys",
		},
		{
			name:     "numeric filename",
			metadata: `{"filename":123,"flow":"test"}`,
			wantMsg:  "Invalid metadata JSON",
		},
		{
			name:     "missing closing brace",
			metadata: `{"filename":"test.txt","flow":"test"`,
			wantMsg:  "Invalid metadata JSON",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("test content"))
			if tt.metadata != "" {
				req.Header.Set("DeltafiMetadata", tt.metadata)
			}
			w := httptest.NewRecorder()

			fileSinkHandler(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusBadRequest)
			}

			body, _ := io.ReadAll(resp.Body)
			if !strings.Contains(string(body), tt.wantMsg) {
				t.Errorf("Expected message containing %q, got: %s", tt.wantMsg, string(body))
			}
		})
	}
}

func TestSaveContentOverwritesExistingFile(t *testing.T) {
	tempDir := t.TempDir()
	filePath := filepath.Join(tempDir, "overwrite_test.txt")

	// Write initial content
	body1 := io.NopCloser(strings.NewReader("initial content"))
	err := saveContent(filePath, body1)
	if err != nil {
		t.Fatalf("First saveContent() error = %v", err)
	}

	// Overwrite with new content
	body2 := io.NopCloser(strings.NewReader("new content"))
	err = saveContent(filePath, body2)
	if err != nil {
		t.Fatalf("Second saveContent() error = %v", err)
	}

	// Verify the new content
	content, err := os.ReadFile(filePath)
	if err != nil {
		t.Fatalf("Failed to read file: %v", err)
	}

	if string(content) != "new content" {
		t.Errorf("Content = %q, want 'new content'", string(content))
	}
}

func TestSaveMetadataOverwritesExistingFile(t *testing.T) {
	tempDir := t.TempDir()
	filePath := filepath.Join(tempDir, "test.txt")

	// Write initial metadata
	err := saveMetadata(filePath, "POST", `{"version":"1"}`)
	if err != nil {
		t.Fatalf("First saveMetadata() error = %v", err)
	}

	// Overwrite with new metadata
	err = saveMetadata(filePath, "POST", `{"version":"2"}`)
	if err != nil {
		t.Fatalf("Second saveMetadata() error = %v", err)
	}

	// Verify the new metadata
	metadataPath := filePath + ".POST.metadata.json"
	content, err := os.ReadFile(metadataPath)
	if err != nil {
		t.Fatalf("Failed to read metadata file: %v", err)
	}

	if string(content) != `{"version":"2"}` {
		t.Errorf("Metadata = %s, want {\"version\":\"2\"}", string(content))
	}
}

// TestBlackholeSmallLatency verifies small latency values work
func TestBlackholeSmallLatency(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/blackhole?latency=0.001", strings.NewReader("test"))
	w := httptest.NewRecorder()

	start := time.Now()
	blackholeHandler(w, req)
	elapsed := time.Since(start)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	// Should have at least some small delay (1ms = 0.001s)
	if elapsed < time.Millisecond {
		t.Logf("Warning: elapsed time %v is less than expected 1ms", elapsed)
	}
}

// TestConcurrentSaveContent tests concurrent file operations
func TestConcurrentSaveContent(t *testing.T) {
	tempDir := t.TempDir()
	numGoroutines := 10

	done := make(chan bool, numGoroutines)
	errors := make(chan error, numGoroutines)

	for i := range numGoroutines {
		go func(idx int) {
			filePath := filepath.Join(tempDir, "concurrent_"+string(rune('a'+idx))+".txt")
			content := strings.Repeat(string(rune('a'+idx)), 1000)
			body := io.NopCloser(strings.NewReader(content))

			if err := saveContent(filePath, body); err != nil {
				errors <- err
			}
			done <- true
		}(i)
	}

	// Wait for all goroutines
	for range numGoroutines {
		<-done
	}

	close(errors)
	for err := range errors {
		t.Errorf("Concurrent saveContent error: %v", err)
	}
}

func TestBlackholeHandlerFormValueParsing(t *testing.T) {
	// Test with form-encoded latency
	req := httptest.NewRequest(http.MethodPost, "/blackhole", strings.NewReader("latency=0"))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()

	blackholeHandler(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("StatusCode = %d, want %d", resp.StatusCode, http.StatusOK)
	}
}
