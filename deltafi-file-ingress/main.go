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
package main

import (
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/fsnotify/fsnotify"
)

type DirWatcher struct {
	watcher   *fsnotify.Watcher
	watchDir  string
	endpoint  string
	watchList map[string]bool
	mu        sync.Mutex
}

func NewDirWatcher(watchDir, endpoint string) (*DirWatcher, error) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, err
	}

	dw := &DirWatcher{
		watcher:   watcher,
		watchDir:  watchDir,
		endpoint:  endpoint,
		watchList: make(map[string]bool),
	}

	return dw, nil
}

func (dw *DirWatcher) Start() {
	dw.processExistingFiles()
	go dw.handleEvents()

	// Start watching the root directory for new subdirectories
	if err := dw.watcher.Add(dw.watchDir); err != nil {
		log.Fatalf("Error watching root directory: %v", err)
	}

	// Scan for existing immediate subdirectories
	if err := dw.scanImmediateSubDirs(); err != nil {
		log.Fatalf("Error scanning subdirectories: %v", err)
	}

	log.Printf("Started watching: %s\n", dw.watchDir)
	select {} // Keep the main goroutine alive
}

func (dw *DirWatcher) processExistingFiles() {
	entries, err := os.ReadDir(dw.watchDir)
	if err != nil {
		log.Printf("Error reading watch directory %s: %v\n", dw.watchDir, err)
		return
	}

	for _, entry := range entries {
		if !entry.IsDir() || strings.HasPrefix(entry.Name(), ".") {
			continue // Skip files & hidden directories
		}

		subDirPath := filepath.Join(dw.watchDir, entry.Name())
		files, err := os.ReadDir(subDirPath)
		if err != nil {
			log.Printf("Error reading subdirectory %s: %v\n", subDirPath, err)
			continue
		}

		for _, file := range files {
			if file.IsDir() || strings.HasPrefix(file.Name(), ".") {
				continue // Skip subdirectories & hidden files
			}

			filePath := filepath.Join(subDirPath, file.Name())
			go dw.streamFile(filePath) // Process existing file
		}
	}
}

func (dw *DirWatcher) handleEvents() {
	for event := range dw.watcher.Events {
		if isHidden(event.Name) {
			continue
		}

		if event.Op&fsnotify.Create == fsnotify.Create {
			fi, err := os.Stat(event.Name)
			if err != nil {
				continue
			}

			// Ignore subdirectories created inside watched subdirectories
			if fi.IsDir() {
				if _, parentWatched := dw.watchList[filepath.Dir(event.Name)]; parentWatched {
					continue
				}
				// New immediate subdirectory inside watchDir -> Add it to watcher
				if filepath.Dir(event.Name) == dw.watchDir {
					dw.addWatch(event.Name)
				}
			} else {
				// Process the newly created file
				go dw.streamFile(event.Name)
			}
		}

		if event.Op&fsnotify.Remove == fsnotify.Remove {
			// If a subdirectory is removed, stop watching it
			if _, exists := dw.watchList[event.Name]; exists {
				dw.removeWatch(event.Name)
			}
		}
	}
}

func (dw *DirWatcher) scanImmediateSubDirs() error {
	entries, err := os.ReadDir(dw.watchDir)
	if err != nil {
		return err
	}

	for _, entry := range entries {
		if entry.IsDir() {
			dirPath := filepath.Join(dw.watchDir, entry.Name())
			if !isHidden(dirPath) {
				dw.addWatch(dirPath)
			}
		}
	}
	return nil
}

func (dw *DirWatcher) addWatch(dir string) {
	dw.mu.Lock()
	defer dw.mu.Unlock()

	if _, exists := dw.watchList[dir]; exists {
		return
	}

	if err := dw.watcher.Add(dir); err != nil {
		log.Printf("Error watching directory: %s, %v\n", dir, err)
		return
	}

	dw.watchList[dir] = true
	log.Printf("Started watching data source: %s\n", dir)
}

func (dw *DirWatcher) removeWatch(dir string) {
	dw.mu.Lock()
	defer dw.mu.Unlock()

	if _, exists := dw.watchList[dir]; !exists {
		return
	}

	if err := dw.watcher.Remove(dir); err != nil {
		log.Printf("Error removing watch from directory: %s, %v\n", dir, err)
		return
	}

	delete(dw.watchList, dir)
	log.Printf("Stopped watching data source: %s\n", dir)
}

// Streams file content in chunks via HTTP POST
func (dw *DirWatcher) streamFile(filePath string) {
	file, err := os.Open(filePath)
	if err != nil {
		log.Printf("Error opening file %s: %v\n", filePath, err)
		return
	}

	success := false // Flag to track successful upload
	defer func() {
		err := file.Close()
		if err != nil {
			return
		}

		if success {
			if err := os.Remove(filePath); err != nil {
				log.Printf("Error deleting file %s: %v\n", filePath, err)
			}
		}
	}()

	parentDir := filepath.Base(filepath.Dir(filePath))
	pr, pw := io.Pipe() // Pipe for streaming

	// Start streaming file contents in a separate goroutine
	go func() {
		defer func(pw *io.PipeWriter) {
			err := pw.Close()
			if err != nil {
				log.Printf("failed closing pipewriter %v", err)
			}
		}(pw)
		if _, err := io.Copy(pw, file); err != nil {
			log.Printf("Error streaming file %s: %v\n", filePath, err)
		}
	}()

	// Send the HTTP request **in the same goroutine**
	req, err := http.NewRequest("POST", dw.endpoint, pr)
	if err != nil {
		log.Printf("Error creating request for %s: %v\n", filePath, err)
		return
	}

	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Filename", filepath.Base(filePath))
	req.Header.Set("dataSource", parentDir)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Printf("Error sending file %s: %v\n", filePath, err)
		return
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			log.Printf("failed closing the body %v", err)
		}
	}(resp.Body)

	if resp.StatusCode >= 400 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		log.Printf("Failed to upload file %s, status: %d, response: %s\n", filePath, resp.StatusCode, string(bodyBytes))
	} else {
		success = true
	}
}

func isHidden(path string) bool {
	return strings.HasPrefix(filepath.Base(path), ".")
}

func main() {
	endpoint, exists := os.LookupEnv("CORE_URL")
	if !exists {
		endpoint = "http://delta-core-service"
	}
	endpoint += "/api/v2/deltafile/ingress"

	watchDir, exists := os.LookupEnv("INGRESS_DIRECTORY")
	if !exists {
		watchDir = "/data/file-ingress"
	}

	log.Printf("Using endpoint: %s and directory: %s\n", endpoint, watchDir)

	dw, err := NewDirWatcher(watchDir, endpoint)
	if err != nil {
		log.Fatalf("Failed to create directory watcher: %v", err)
	}

	dw.Start()
}
