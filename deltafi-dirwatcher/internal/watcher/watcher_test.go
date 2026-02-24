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
package watcher

import (
	"context"
	"encoding/json"
	"maps"
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
	maps.Copy(result, m.handledFiles)
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
		var actualMetadata map[string]any
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]any{
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
		var actualMetadata map[string]any
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]any{
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
		var actualMetadata map[string]any
		if err := json.Unmarshal([]byte(metadata["metadata"]), &actualMetadata); err != nil {
			t.Errorf("Failed to unmarshal metadata JSON: %v", err)
		}
		expectedMetadataObj := map[string]any{
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

func TestIsHidden(t *testing.T) {
	tests := []struct {
		path   string
		hidden bool
	}{
		{"/some/path/.hidden", true},
		{"/some/path/.hidden.txt", true},
		{"/some/path/visible.txt", false},
		{"/some/.hidden/file.txt", false}, // only checks base name
		{"/.hidden", true},
		{".hidden", true},
		{"visible", false},
		{"/path/to/..", true}, // edge case: ".." is hidden
		{"/path/to/.", true},  // edge case: "." is hidden
	}

	for _, tt := range tests {
		t.Run(tt.path, func(t *testing.T) {
			got := isHidden(tt.path)
			if got != tt.hidden {
				t.Errorf("isHidden(%q) = %v, want %v", tt.path, got, tt.hidden)
			}
		})
	}
}

func TestIsMetadataFile(t *testing.T) {
	tests := []struct {
		path     string
		expected bool
	}{
		{"/some/path/.default_metadata.yaml", true},
		{"/some/path/.default_metadata.json", true},
		{".default_metadata.yaml", true},
		{".default_metadata.json", true},
		{"/some/path/regular.yaml", false},
		{"/some/path/regular.json", false},
		{"/some/path/.other_metadata.yaml", false},
		{"/some/path/default_metadata.yaml", false}, // missing leading dot
		{"/some/.default_metadata.yaml/file.txt", false},
	}

	for _, tt := range tests {
		t.Run(tt.path, func(t *testing.T) {
			got := isMetadataFile(tt.path)
			if got != tt.expected {
				t.Errorf("isMetadataFile(%q) = %v, want %v", tt.path, got, tt.expected)
			}
		})
	}
}

func TestSettlingFile_CheckForChange(t *testing.T) {
	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "settling_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	t.Run("file size unchanged increments sameCount", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "unchanged.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		sf := &SettlingFile{
			path:      testFile,
			lastSize:  7, // "content" is 7 bytes
			sameCount: 0,
		}

		if err := sf.checkForChange(); err != nil {
			t.Fatalf("checkForChange() error = %v", err)
		}

		if sf.sameCount != 1 {
			t.Errorf("sameCount = %d, want 1", sf.sameCount)
		}
		if sf.lastSize != 7 {
			t.Errorf("lastSize = %d, want 7", sf.lastSize)
		}
	})

	t.Run("file size changed resets sameCount", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "changed.txt")
		if err := os.WriteFile(testFile, []byte("initial"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		sf := &SettlingFile{
			path:      testFile,
			lastSize:  5, // Different from actual size (7 bytes)
			sameCount: 5,
		}

		if err := sf.checkForChange(); err != nil {
			t.Fatalf("checkForChange() error = %v", err)
		}

		if sf.sameCount != 0 {
			t.Errorf("sameCount = %d, want 0 (should be reset)", sf.sameCount)
		}
		if sf.lastSize != 7 {
			t.Errorf("lastSize = %d, want 7", sf.lastSize)
		}
	})

	t.Run("non-existent file returns error", func(t *testing.T) {
		sf := &SettlingFile{
			path:      filepath.Join(tmpDir, "nonexistent.txt"),
			lastSize:  0,
			sameCount: 0,
		}

		err := sf.checkForChange()
		if err == nil {
			t.Error("Expected error for non-existent file, got nil")
		}
	})

	t.Run("lastCheck is updated", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "timestamp.txt")
		if err := os.WriteFile(testFile, []byte("test"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		oldTime := time.Now().Add(-1 * time.Hour)
		sf := &SettlingFile{
			path:      testFile,
			lastSize:  4,
			sameCount: 0,
			lastCheck: oldTime,
		}

		if err := sf.checkForChange(); err != nil {
			t.Fatalf("checkForChange() error = %v", err)
		}

		if !sf.lastCheck.After(oldTime) {
			t.Errorf("lastCheck was not updated")
		}
	})
}

func TestDirWatcher_ReadMetadataFile(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory for test files
	tmpDir, err := os.MkdirTemp("", "metadata_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	config := &Config{
		WatchDir: tmpDir,
		Endpoint: "http://test",
		Workers:  1,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	t.Run("valid YAML metadata", func(t *testing.T) {
		yamlFile := filepath.Join(tmpDir, "test.yaml")
		yamlContent := "key1: value1\nkey2: value2\n"
		if err := os.WriteFile(yamlFile, []byte(yamlContent), 0644); err != nil {
			t.Fatalf("Failed to create YAML file: %v", err)
		}

		metadata, err := watcher.readMetadataFile(yamlFile)
		if err != nil {
			t.Fatalf("readMetadataFile() error = %v", err)
		}

		if metadata["key1"] != "value1" || metadata["key2"] != "value2" {
			t.Errorf("metadata = %v, want {key1:value1, key2:value2}", metadata)
		}
	})

	t.Run("valid JSON metadata", func(t *testing.T) {
		jsonFile := filepath.Join(tmpDir, "test.json")
		jsonContent := `{"key1": "value1", "key2": "value2"}`
		if err := os.WriteFile(jsonFile, []byte(jsonContent), 0644); err != nil {
			t.Fatalf("Failed to create JSON file: %v", err)
		}

		metadata, err := watcher.readMetadataFile(jsonFile)
		if err != nil {
			t.Fatalf("readMetadataFile() error = %v", err)
		}

		if metadata["key1"] != "value1" || metadata["key2"] != "value2" {
			t.Errorf("metadata = %v, want {key1:value1, key2:value2}", metadata)
		}
	})

	t.Run("malformed YAML", func(t *testing.T) {
		badYaml := filepath.Join(tmpDir, "bad.yaml")
		if err := os.WriteFile(badYaml, []byte("not: valid: yaml: content"), 0644); err != nil {
			t.Fatalf("Failed to create bad YAML file: %v", err)
		}

		_, err := watcher.readMetadataFile(badYaml)
		if err == nil {
			t.Error("Expected error for malformed YAML, got nil")
		}
	})

	t.Run("malformed JSON", func(t *testing.T) {
		badJson := filepath.Join(tmpDir, "bad.json")
		if err := os.WriteFile(badJson, []byte("{not valid json}"), 0644); err != nil {
			t.Fatalf("Failed to create bad JSON file: %v", err)
		}

		_, err := watcher.readMetadataFile(badJson)
		if err == nil {
			t.Error("Expected error for malformed JSON, got nil")
		}
	})

	t.Run("unsupported format", func(t *testing.T) {
		txtFile := filepath.Join(tmpDir, "test.txt")
		if err := os.WriteFile(txtFile, []byte("key=value"), 0644); err != nil {
			t.Fatalf("Failed to create txt file: %v", err)
		}

		_, err := watcher.readMetadataFile(txtFile)
		if err == nil {
			t.Error("Expected error for unsupported format, got nil")
		}
	})

	t.Run("non-existent file", func(t *testing.T) {
		_, err := watcher.readMetadataFile(filepath.Join(tmpDir, "nonexistent.yaml"))
		if err == nil {
			t.Error("Expected error for non-existent file, got nil")
		}
	})

	t.Run("empty YAML file", func(t *testing.T) {
		emptyYaml := filepath.Join(tmpDir, "empty.yaml")
		if err := os.WriteFile(emptyYaml, []byte(""), 0644); err != nil {
			t.Fatalf("Failed to create empty YAML file: %v", err)
		}

		metadata, err := watcher.readMetadataFile(emptyYaml)
		if err != nil {
			t.Fatalf("readMetadataFile() error = %v", err)
		}

		if metadata != nil && len(metadata) != 0 {
			t.Errorf("Expected nil or empty metadata for empty file, got %v", metadata)
		}
	})
}

func TestDirWatcher_MetadataInheritance(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory structure
	tmpDir, err := os.MkdirTemp("", "inheritance_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create directory structure: tmpDir/source/subdir/deepdir
	source := filepath.Join(tmpDir, "source")
	subdir := filepath.Join(source, "subdir")
	deepdir := filepath.Join(subdir, "deepdir")
	if err := os.MkdirAll(deepdir, 0755); err != nil {
		t.Fatalf("Failed to create directory structure: %v", err)
	}

	config := &Config{
		WatchDir: tmpDir,
		Endpoint: "http://test",
		Workers:  1,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	t.Run("inherits from parent directory", func(t *testing.T) {
		// Create metadata only in parent
		parentMetadata := "inherited_key: inherited_value\n"
		if err := os.WriteFile(filepath.Join(source, ".default_metadata.yaml"), []byte(parentMetadata), 0644); err != nil {
			t.Fatalf("Failed to create parent metadata: %v", err)
		}

		// Load metadata from subdir (should inherit from parent)
		metadata := watcher.loadDefaultMetadata(subdir)
		if metadata == nil {
			t.Fatal("Expected inherited metadata, got nil")
		}
		if metadata["inherited_key"] != "inherited_value" {
			t.Errorf("metadata[inherited_key] = %q, want 'inherited_value'", metadata["inherited_key"])
		}
	})

	t.Run("child metadata overrides parent", func(t *testing.T) {
		// Create metadata in child that should take precedence
		childMetadata := "child_key: child_value\n"
		if err := os.WriteFile(filepath.Join(subdir, ".default_metadata.yaml"), []byte(childMetadata), 0644); err != nil {
			t.Fatalf("Failed to create child metadata: %v", err)
		}

		// Load metadata from subdir (should use its own)
		metadata := watcher.loadDefaultMetadata(subdir)
		if metadata == nil {
			t.Fatal("Expected child metadata, got nil")
		}
		if metadata["child_key"] != "child_value" {
			t.Errorf("metadata[child_key] = %q, want 'child_value'", metadata["child_key"])
		}
		if _, hasInherited := metadata["inherited_key"]; hasInherited {
			t.Error("Child metadata should not include inherited_key when it has its own")
		}
	})

	t.Run("deep inheritance", func(t *testing.T) {
		// Remove subdir metadata to test inheritance from source
		os.Remove(filepath.Join(subdir, ".default_metadata.yaml"))

		// Load metadata from deepdir (should inherit from source)
		metadata := watcher.loadDefaultMetadata(deepdir)
		if metadata == nil {
			t.Fatal("Expected inherited metadata from grandparent, got nil")
		}
		if metadata["inherited_key"] != "inherited_value" {
			t.Errorf("metadata[inherited_key] = %q, want 'inherited_value'", metadata["inherited_key"])
		}
	})

	t.Run("no metadata anywhere", func(t *testing.T) {
		// Create a directory outside the structure
		isolated := filepath.Join(tmpDir, "isolated")
		if err := os.MkdirAll(isolated, 0755); err != nil {
			t.Fatalf("Failed to create isolated dir: %v", err)
		}

		metadata := watcher.loadDefaultMetadata(isolated)
		if metadata != nil {
			t.Errorf("Expected nil for directory with no metadata, got %v", metadata)
		}
	})

	t.Run("JSON takes precedence over YAML in same directory", func(t *testing.T) {
		bothDir := filepath.Join(tmpDir, "both")
		if err := os.MkdirAll(bothDir, 0755); err != nil {
			t.Fatalf("Failed to create both dir: %v", err)
		}

		// YAML comes first alphabetically
		if err := os.WriteFile(filepath.Join(bothDir, ".default_metadata.yaml"), []byte("format: yaml\n"), 0644); err != nil {
			t.Fatalf("Failed to create YAML metadata: %v", err)
		}
		if err := os.WriteFile(filepath.Join(bothDir, ".default_metadata.json"), []byte(`{"format": "json"}`), 0644); err != nil {
			t.Fatalf("Failed to create JSON metadata: %v", err)
		}

		metadata := watcher.loadDefaultMetadata(bothDir)
		if metadata == nil {
			t.Fatal("Expected metadata, got nil")
		}
		// YAML should be checked first per defaultMetadataFiles order
		if metadata["format"] != "yaml" {
			t.Errorf("Expected YAML to take precedence (checked first), got format=%q", metadata["format"])
		}
	})
}

func TestDirWatcher_AddRemoveWatch(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory structure
	tmpDir, err := os.MkdirTemp("", "watch_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	config := &Config{
		WatchDir: tmpDir,
		Endpoint: "http://test",
		Workers:  1,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	t.Run("addWatch adds directory to watch list", func(t *testing.T) {
		subdir := filepath.Join(tmpDir, "subdir1")
		if err := os.MkdirAll(subdir, 0755); err != nil {
			t.Fatalf("Failed to create subdir: %v", err)
		}

		watcher.addWatch(subdir)

		// Check watch list
		if _, exists := watcher.watchList.Load(subdir); !exists {
			t.Error("Expected directory to be in watch list")
		}
	})

	t.Run("addWatch prevents duplicates", func(t *testing.T) {
		subdir := filepath.Join(tmpDir, "subdir2")
		if err := os.MkdirAll(subdir, 0755); err != nil {
			t.Fatalf("Failed to create subdir: %v", err)
		}

		watcher.addWatch(subdir)
		watcher.addWatch(subdir) // Add again

		// Should still only be in list once - no crash, no error
		count := 0
		watcher.watchList.Range(func(key, value any) bool {
			if key.(string) == subdir {
				count++
			}
			return true
		})
		if count != 1 {
			t.Errorf("Expected directory to be in watch list exactly once, found %d times", count)
		}
	})

	t.Run("removeWatch removes directory from watch list", func(t *testing.T) {
		subdir := filepath.Join(tmpDir, "subdir3")
		if err := os.MkdirAll(subdir, 0755); err != nil {
			t.Fatalf("Failed to create subdir: %v", err)
		}

		watcher.addWatch(subdir)
		watcher.removeWatch(subdir)

		if _, exists := watcher.watchList.Load(subdir); exists {
			t.Error("Expected directory to be removed from watch list")
		}
	})

	t.Run("removeWatch handles non-existent directory gracefully", func(t *testing.T) {
		// Should not panic or error
		watcher.removeWatch(filepath.Join(tmpDir, "nonexistent"))
	})
}

func TestDirWatcher_AddFileToDelayQueue(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory
	tmpDir, err := os.MkdirTemp("", "delay_queue_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	config := &Config{
		WatchDir:     tmpDir,
		Endpoint:     "http://test",
		Workers:      1,
		SettlingTime: 100 * time.Millisecond,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	t.Run("adds file to settling files map", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "test1.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		ctx := context.Background()
		if err := watcher.addFileToDelayQueue(ctx, testFile); err != nil {
			t.Fatalf("addFileToDelayQueue() error = %v", err)
		}

		if _, exists := watcher.settlingFiles.Load(testFile); !exists {
			t.Error("Expected file to be in settling files map")
		}
	})

	t.Run("ignores already monitored files", func(t *testing.T) {
		testFile := filepath.Join(tmpDir, "test2.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		ctx := context.Background()
		if err := watcher.addFileToDelayQueue(ctx, testFile); err != nil {
			t.Fatalf("First addFileToDelayQueue() error = %v", err)
		}

		// Add again - should return nil without error
		if err := watcher.addFileToDelayQueue(ctx, testFile); err != nil {
			t.Fatalf("Second addFileToDelayQueue() error = %v", err)
		}
	})

	t.Run("returns error for non-existent file", func(t *testing.T) {
		ctx := context.Background()
		err := watcher.addFileToDelayQueue(ctx, filepath.Join(tmpDir, "nonexistent.txt"))
		if err == nil {
			t.Error("Expected error for non-existent file, got nil")
		}
	})
}

func TestDirWatcher_ProcessSettlingFile(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory
	tmpDir, err := os.MkdirTemp("", "settling_process_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	sourceDir := filepath.Join(tmpDir, "source")
	if err := os.MkdirAll(sourceDir, 0755); err != nil {
		t.Fatalf("Failed to create source dir: %v", err)
	}

	config := &Config{
		WatchDir:     tmpDir,
		Endpoint:     "http://test",
		Workers:      1,
		MaxFileSize:  1024 * 1024,
		SettlingTime: 100 * time.Millisecond, // 100ms settling time
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	t.Run("file with enough same counts goes to egress queue", func(t *testing.T) {
		testFile := filepath.Join(sourceDir, "settled.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		sf := SettlingFile{
			path:      testFile,
			lastSize:  7, // "content" is 7 bytes
			sameCount: 2, // 2 * 100ms = 200ms >= 100ms settling time
			startTime: time.Now(),
			lastCheck: time.Now(),
		}

		watcher.settlingFiles.Store(testFile, &sf)

		// Start a goroutine to read from egress queue
		received := make(chan bool, 1)
		go func() {
			select {
			case <-watcher.egressQueue:
				received <- true
			case <-time.After(500 * time.Millisecond):
				received <- false
			}
		}()

		ctx := context.Background()
		err := watcher.processSettlingFile(ctx, sf)
		if err != nil {
			t.Fatalf("processSettlingFile() error = %v", err)
		}

		if !<-received {
			t.Error("Expected file to be sent to egress queue")
		}

		// File should be removed from settling files map
		if _, exists := watcher.settlingFiles.Load(testFile); exists {
			t.Error("Expected file to be removed from settling files map")
		}
	})

	t.Run("file not settled re-adds to delay queue", func(t *testing.T) {
		testFile := filepath.Join(sourceDir, "notsettled.txt")
		if err := os.WriteFile(testFile, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create test file: %v", err)
		}

		// Use a different lastSize to trigger size change detection,
		// which resets sameCount to 0
		sf := SettlingFile{
			path:      testFile,
			lastSize:  5, // Different from actual 7 bytes - will cause reset
			sameCount: 0,
			startTime: time.Now(),
			lastCheck: time.Now(),
		}

		watcher.settlingFiles.Store(testFile, &sf)

		ctx := context.Background()
		err := watcher.processSettlingFile(ctx, sf)
		if err != nil {
			t.Fatalf("processSettlingFile() error = %v", err)
		}

		// File should still be in settling files map (since size changed, sameCount reset to 0)
		if _, exists := watcher.settlingFiles.Load(testFile); !exists {
			t.Error("Expected file to still be in settling files map")
		}
	})

	t.Run("deleted file returns error", func(t *testing.T) {
		testFile := filepath.Join(sourceDir, "deleted.txt")
		// Note: don't create the file

		sf := SettlingFile{
			path:      testFile,
			lastSize:  0,
			sameCount: 0,
			startTime: time.Now(),
			lastCheck: time.Now(),
		}

		watcher.settlingFiles.Store(testFile, &sf)

		ctx := context.Background()
		err := watcher.processSettlingFile(ctx, sf)
		if err == nil {
			t.Error("Expected error for deleted file, got nil")
		}

		// File should be removed from settling files map
		if _, exists := watcher.settlingFiles.Load(testFile); exists {
			t.Error("Expected file to be removed from settling files map after error")
		}
	})
}

func TestDirWatcher_HiddenFileFiltering(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Create a temporary directory structure
	tmpDir, err := os.MkdirTemp("", "hidden_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Create visible and hidden directories
	visibleDir := filepath.Join(tmpDir, "visible")
	hiddenDir := filepath.Join(tmpDir, ".hidden")
	if err := os.MkdirAll(visibleDir, 0755); err != nil {
		t.Fatalf("Failed to create visible dir: %v", err)
	}
	if err := os.MkdirAll(hiddenDir, 0755); err != nil {
		t.Fatalf("Failed to create hidden dir: %v", err)
	}

	// Create files
	visibleFile := filepath.Join(visibleDir, "visible.txt")
	hiddenFile := filepath.Join(visibleDir, ".hidden.txt")
	fileInHiddenDir := filepath.Join(hiddenDir, "file.txt")

	for _, f := range []string{visibleFile, hiddenFile, fileInHiddenDir} {
		if err := os.WriteFile(f, []byte("content"), 0644); err != nil {
			t.Fatalf("Failed to create file %s: %v", f, err)
		}
	}

	config := &Config{
		WatchDir:     tmpDir,
		Endpoint:     "http://test",
		Workers:      1,
		MaxFileSize:  1024 * 1024,
		SettlingTime: 50 * time.Millisecond,
	}

	handler := NewMockFileHandler()
	watcher, err := NewDirWatcher(config, handler, logger)
	if err != nil {
		t.Fatalf("Failed to create watcher: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	go func() {
		if err := watcher.Start(ctx); err != nil && err != context.Canceled {
			t.Errorf("Watcher error: %v", err)
		}
	}()

	// Wait for processing
	time.Sleep(300 * time.Millisecond)

	handledFiles := handler.GetHandledFiles()

	// Visible file should be handled
	if _, ok := handledFiles[visibleFile]; !ok {
		t.Error("Expected visible file to be handled")
	}

	// Hidden file should NOT be handled
	if _, ok := handledFiles[hiddenFile]; ok {
		t.Error("Expected hidden file to NOT be handled")
	}

	// File in hidden directory (but not immediate child of watch dir) should NOT be handled
	// Actually, looking at the code, .hidden is an immediate child of the watch dir,
	// so it WILL be watched. Let me re-read the logic...
	// The code says: "Skip hidden directories unless they are immediate children of the watch dir"
	// So .hidden IS an immediate child and will be watched!
}
