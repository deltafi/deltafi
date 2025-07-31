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
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"sync"
	"testing"
	"time"

	"go.uber.org/zap"
)

// MockFileHandler implements FileHandler interface for testing
type MockFileHandler struct {
	mu           sync.Mutex
	handledFiles map[string]map[string]string // map[filepath]metadata
	shouldError  bool
}

func NewMockFileHandler() *MockFileHandler {
	return &MockFileHandler{
		handledFiles: make(map[string]map[string]string),
	}
}

func (m *MockFileHandler) HandleFile(ctx context.Context, path string, metadata map[string]string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.handledFiles[path] = metadata
	return nil
}

func (m *MockFileHandler) GetHandledFiles() map[string]map[string]string {
	m.mu.Lock()
	defer m.mu.Unlock()
	result := make(map[string]map[string]string)
	for k, v := range m.handledFiles {
		result[k] = v
	}
	return result
}

func TestDirWatcher_ProcessExistingFiles(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create temporary test directories
	tmpDir, err := os.MkdirTemp("", "watcher_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create test structure
	source1 := filepath.Join(tmpDir, "source1")
	source2 := filepath.Join(tmpDir, "source2")
	if err := os.MkdirAll(source1, 0755); err != nil {
		t.Fatalf("Failed to create source1 dir: %v", err)
	}
	if err := os.MkdirAll(source2, 0755); err != nil {
		t.Fatalf("Failed to create source2 dir: %v", err)
	}

	// Create test files
	files := map[string]string{
		filepath.Join(source1, "file1.txt"): "content1",
		filepath.Join(source1, "file2.txt"): "content2",
		filepath.Join(source2, "file3.txt"): "content3",
	}

	for path, content := range files {
		if err := os.WriteFile(path, []byte(content), 0644); err != nil {
			t.Fatalf("Failed to create test file %s: %v", path, err)
		}
	}

	config := &Config{
		WatchDir:     tmpDir,
		Endpoint:     "http://test",
		Workers:      1,
		BufferSize:   1024,
		MaxFileSize:  1024 * 1024,
		SettlingTime: 50, // Use a short settling time for testing
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Start the watcher to process settling files and egress queue
	go func() {
		if err := watcher.Start(ctx); err != nil && err != context.Canceled {
			t.Errorf("Unexpected error from watcher.Start(): %v", err)
		}
	}()

	// Wait for watcher to start
	time.Sleep(100 * time.Millisecond)

	// Process existing files (now adds them to delay queue)
	if err := watcher.processExistingFiles(ctx); err != nil {
		t.Fatalf("Failed to process existing files: %v", err)
	}

	// Wait for files to settle and be processed
	// With 50ms settling time, we need to wait at least 100ms (2 * settling time)
	time.Sleep(300 * time.Millisecond)

	// Verify handled files
	handledFiles := handler.GetHandledFiles()
	if len(handledFiles) != len(files) {
		t.Errorf("Expected %d files to be handled, got %d", len(files), len(handledFiles))
	}

	for path := range files {
		if _, ok := handledFiles[path]; !ok {
			t.Errorf("Expected file %s to be handled", path)
		}
		metadata := handledFiles[path]
		expectedSource := filepath.Base(filepath.Dir(path))
		if metadata["dataSource"] != expectedSource {
			t.Errorf("Expected dataSource %s, got %s", expectedSource, metadata["dataSource"])
		}
	}
}

func TestDirWatcher_HandleEvents(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create temporary test directory
	tmpDir, err := os.MkdirTemp("", "watcher_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	config := &Config{
		WatchDir:     tmpDir,
		Endpoint:     "http://test",
		Workers:      1,
		BufferSize:   1024,
		MaxFileSize:  1024 * 1024,
		SettlingTime: 50, // Use a short settling time for testing
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	// Create a done channel to signal test completion
	done := make(chan struct{})

	// Start the watcher
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go func() {
		err := watcher.Start(ctx)
		if err != nil && err != context.Canceled {
			t.Errorf("Unexpected error from watcher.Start(): %v", err)
		}
		close(done)
	}()

	// Wait for watcher to start
	time.Sleep(100 * time.Millisecond)

	// Create a new source directory
	sourceDir := filepath.Join(tmpDir, "newsource")
	if err := os.MkdirAll(sourceDir, 0755); err != nil {
		t.Fatalf("Failed to create source dir: %v", err)
	}

	// Wait for directory to be watched
	time.Sleep(100 * time.Millisecond)

	// Create a new file
	testFile := filepath.Join(sourceDir, "test.txt")
	if err := os.WriteFile(testFile, []byte("test content"), 0644); err != nil {
		t.Fatalf("Failed to create test file: %v", err)
	}

	// Wait for file to be processed (needs time for settling and egress processing)
	time.Sleep(500 * time.Millisecond)

	// Cancel context and wait for watcher to stop
	cancel()
	<-done

	// Verify the file was handled
	handledFiles := handler.GetHandledFiles()
	if _, ok := handledFiles[testFile]; !ok {
		t.Errorf("Expected file %s to be handled", testFile)
	}

	metadata := handledFiles[testFile]
	if metadata["dataSource"] != "newsource" {
		t.Errorf("Expected dataSource newsource, got %s", metadata["dataSource"])
	}
}

func TestDirWatcher_FileSizeLimit(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create temporary test directory
	tmpDir, err := os.MkdirTemp("", "watcher_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create source directory
	sourceDir := filepath.Join(tmpDir, "source")
	if err := os.MkdirAll(sourceDir, 0755); err != nil {
		t.Fatalf("Failed to create source dir: %v", err)
	}

	config := &Config{
		WatchDir:    tmpDir,
		Endpoint:    "http://test",
		Workers:     1,
		BufferSize:  1024,
		MaxFileSize: 10, // Set a small max file size for testing
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	// Create a file larger than the max size
	largeFile := filepath.Join(sourceDir, "large.txt")
	if err := os.WriteFile(largeFile, []byte("this is more than 10 bytes"), 0644); err != nil {
		t.Fatalf("Failed to create large file: %v", err)
	}

	ctx := context.Background()

	// Create SettlingFile for large file
	largeSettlingFile := SettlingFile{
		path:      largeFile,
		lastSize:  0,
		sameCount: 0,
		startTime: time.Now(),
		lastCheck: time.Now(),
	}

	err = watcher.processFile(ctx, largeSettlingFile)
	if err == nil {
		t.Error("Expected error for file size limit, got nil")
	}

	// Create a file within the size limit
	smallFile := filepath.Join(sourceDir, "small.txt")
	if err := os.WriteFile(smallFile, []byte("small"), 0644); err != nil {
		t.Fatalf("Failed to create small file: %v", err)
	}

	// Create SettlingFile for small file
	smallSettlingFile := SettlingFile{
		path:      smallFile,
		lastSize:  0,
		sameCount: 0,
		startTime: time.Now(),
		lastCheck: time.Now(),
	}

	err = watcher.processFile(ctx, smallSettlingFile)
	if err != nil {
		t.Errorf("Unexpected error for small file: %v", err)
	}
}

func TestDirWatcher_DefaultMetadata(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create temporary test directories
	tmpDir, err := os.MkdirTemp("", "watcher_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create test structure
	source1 := filepath.Join(tmpDir, "source1")
	source2 := filepath.Join(tmpDir, "source2")
	if err := os.MkdirAll(source1, 0755); err != nil {
		t.Fatalf("Failed to create source1 dir: %v", err)
	}
	if err := os.MkdirAll(source2, 0755); err != nil {
		t.Fatalf("Failed to create source2 dir: %v", err)
	}

	// Create default metadata files
	yamlMetadata := `
environment: test
priority: high
`
	if err := os.WriteFile(filepath.Join(source1, ".default_metadata.yaml"), []byte(yamlMetadata), 0644); err != nil {
		t.Fatalf("Failed to create YAML metadata file: %v", err)
	}

	jsonMetadata := `{
		"environment": "prod",
		"priority": "low"
	}`
	if err := os.WriteFile(filepath.Join(source2, ".default_metadata.json"), []byte(jsonMetadata), 0644); err != nil {
		t.Fatalf("Failed to create JSON metadata file: %v", err)
	}

	// Create test files
	files := map[string]string{
		filepath.Join(source1, "file1.txt"): "content1",
		filepath.Join(source2, "file2.txt"): "content2",
	}

	for path, content := range files {
		if err := os.WriteFile(path, []byte(content), 0644); err != nil {
			t.Fatalf("Failed to create test file %s: %v", path, err)
		}
	}

	config := &Config{
		WatchDir:    tmpDir,
		Endpoint:    "http://test",
		Workers:     1,
		BufferSize:  1024,
		MaxFileSize: 1024 * 1024,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Start the watcher in a goroutine
	watcherErrCh := make(chan error, 1)
	go func() {
		watcherErrCh <- watcher.Start(ctx)
	}()

	// Wait for initial processing (files need time to settle)
	time.Sleep(300 * time.Millisecond)

	// Verify handled files and their metadata
	handledFiles := handler.GetHandledFiles()
	if len(handledFiles) != len(files) {
		t.Errorf("Expected %d files to be handled, got %d", len(files), len(handledFiles))
	}

	// Check source1 file metadata (YAML)
	file1Path := filepath.Join(source1, "file1.txt")
	if metadata, ok := handledFiles[file1Path]; !ok {
		t.Errorf("Expected file %s to be handled", file1Path)
	} else {
		var actualMetadata map[string]interface{}
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]interface{}{
			"environment": "test",
			"priority":    "high",
		}
		if !reflect.DeepEqual(actualMetadata, expectedMetadataObj) {
			t.Errorf("Expected metadata object %v, got %v", expectedMetadataObj, actualMetadata)
		}
		if metadata["dataSource"] != "source1" {
			t.Errorf("Expected dataSource source1, got %s", metadata["dataSource"])
		}
	}

	// Check source2 file metadata (JSON)
	file2Path := filepath.Join(source2, "file2.txt")
	if metadata, ok := handledFiles[file2Path]; !ok {
		t.Errorf("Expected file %s to be handled", file2Path)
	} else {
		var actualMetadata map[string]interface{}
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]interface{}{
			"environment": "prod",
			"priority":    "low",
		}
		if !reflect.DeepEqual(actualMetadata, expectedMetadataObj) {
			t.Errorf("Expected metadata object %v, got %v", expectedMetadataObj, actualMetadata)
		}
		if metadata["dataSource"] != "source2" {
			t.Errorf("Expected dataSource source2, got %s", metadata["dataSource"])
		}
	}

	// Test metadata file update
	updatedYamlMetadata := `
environment: staging
priority: medium
newfield: value
`
	if err := os.WriteFile(filepath.Join(source1, ".default_metadata.yaml"), []byte(updatedYamlMetadata), 0644); err != nil {
		t.Fatalf("Failed to update YAML metadata file: %v", err)
	}

	// Wait for metadata update to be processed
	time.Sleep(100 * time.Millisecond)

	// Create a new file after metadata update
	newFilePath := filepath.Join(source1, "file3.txt")
	if err := os.WriteFile(newFilePath, []byte("content3"), 0644); err != nil {
		t.Fatalf("Failed to create new test file: %v", err)
	}

	// Wait for file processing (files need time to settle)
	time.Sleep(300 * time.Millisecond)

	// Verify the new file has updated metadata
	handledFiles = handler.GetHandledFiles()
	if metadata, ok := handledFiles[newFilePath]; !ok {
		t.Errorf("Expected file %s to be handled", newFilePath)
	} else {
		var actualMetadata map[string]interface{}
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]interface{}{
			"environment": "staging",
			"priority":    "medium",
			"newfield":    "value",
		}
		if !reflect.DeepEqual(actualMetadata, expectedMetadataObj) {
			t.Errorf("Expected metadata object %v, got %v", expectedMetadataObj, actualMetadata)
		}
		if metadata["dataSource"] != "source1" {
			t.Errorf("Expected dataSource source1, got %s", metadata["dataSource"])
		}
	}

	// Clean up
	cancel()
	if err := <-watcherErrCh; err != nil && err != context.Canceled {
		t.Errorf("Watcher error: %v", err)
	}
}
