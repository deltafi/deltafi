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
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"gopkg.in/yaml.v3"
)

// defaultMetadataFiles contains the possible filenames for default metadata
var defaultMetadataFiles = []string{".default_metadata.yaml", ".default_metadata.json"}

// FileHandler defines the interface for handling discovered files
type FileHandler interface {
	HandleFile(ctx context.Context, path string, metadata map[string]string) error
}

// DirWatcher watches directories for new files and processes them
type DirWatcher struct {
	config         *Config
	watcher        *fsnotify.Watcher
	handler        FileHandler
	logger         *zap.Logger
	watchList      sync.Map
	metadataMap    sync.Map // map[string]map[string]string - directory path to metadata
	settlingFiles  sync.Map // map[string]*SettlingFile - path to settling file info
	workerGroup    *errgroup.Group
	delayQueue     *DelayQueue[*SettlingFile]
	egressQueue    chan SettlingFile // Channel for settled files ready to be written out
	egressWorkers  int
	periodicTicker *time.Ticker // Ticker for periodic processExistingFiles invocation
}

type SettlingFile struct {
	path      string
	lastSize  int64
	sameCount int
	startTime time.Time
	lastCheck time.Time
}

func (sf *SettlingFile) checkForChange() error {
	fi, err := os.Stat(sf.path)
	if err != nil {
		return err
	}

	if fi.Size() == sf.lastSize {
		sf.sameCount++
	} else {
		sf.lastSize = fi.Size()
		sf.sameCount = 0
	}
	sf.lastCheck = time.Now()

	return nil
}

// loadDefaultMetadata attempts to load default metadata from a directory and its parents
func (dw *DirWatcher) loadDefaultMetadata(dir string) map[string]string {
	// First try to load from the current directory
	for _, filename := range defaultMetadataFiles {
		metadataPath := filepath.Join(dir, filename)
		metadata, err := dw.readMetadataFile(metadataPath)
		if err == nil {
			return metadata
		}
		if !os.IsNotExist(err) {
			dw.logger.Error("Failed to read metadata file",
				zap.String("file", metadataPath),
				zap.Error(err))
		}
	}

	// If no metadata found and we're not at the root watch dir, try parent
	parent := filepath.Dir(dir)
	if parent != dw.config.WatchDir && parent != dir {
		return dw.loadDefaultMetadata(parent)
	}

	return nil
}

// readMetadataFile reads and parses a metadata file (YAML or JSON)
func (dw *DirWatcher) readMetadataFile(path string) (map[string]string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var metadata map[string]string
	ext := strings.ToLower(filepath.Ext(path))

	switch ext {
	case ".yaml":
		err = yaml.Unmarshal(data, &metadata)
	case ".json":
		err = json.Unmarshal(data, &metadata)
	default:
		return nil, fmt.Errorf("unsupported metadata file format: %s", ext)
	}

	if err != nil {
		return nil, fmt.Errorf("failed to parse metadata file: %w", err)
	}

	return metadata, nil
}

// NewDirWatcher creates a new directory watcher
func NewDirWatcher(config *Config, handler FileHandler, logger *zap.Logger) (*DirWatcher, error) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, fmt.Errorf("failed to create watcher: %w", err)
	}

	dw := &DirWatcher{
		config:        config,
		watcher:       watcher,
		handler:       handler,
		logger:        logger,
		watchList:     sync.Map{},
		workerGroup:   &errgroup.Group{},
		egressQueue:   make(chan SettlingFile, 10000), // Buffer size for the egress queue
		egressWorkers: config.Workers,
	}

	// Create the delay queue with the settling file processor
	dw.delayQueue = NewDelayQueue(func(sf *SettlingFile) error {
		return dw.processSettlingFile(context.Background(), *sf)
	}, logger)

	return dw, nil
}

// addFileToDelayQueue adds a new file to the delay queue for settling monitoring
func (dw *DirWatcher) addFileToDelayQueue(_ context.Context, path string) error {
	// Check if file already exists
	if _, exists := dw.settlingFiles.Load(path); exists {
		return nil // Already being monitored
	}

	// Get initial file info
	fi, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("file disappeared: %w", err)
		}
		return fmt.Errorf("failed to stat file: %w", err)
	}

	// Create new settling file entry
	sf := SettlingFile{
		path:      path,
		lastSize:  fi.Size(),
		sameCount: 0,
		startTime: time.Now(),
		lastCheck: time.Now(),
	}

	// Store in map
	dw.settlingFiles.Store(path, &sf)

	// Add to delay queue for initial 100ms delay
	dw.delayQueue.Add(&sf, 100*time.Millisecond)

	return nil
}

// processSettlingFile processes a settling file from the delay queue
func (dw *DirWatcher) processSettlingFile(ctx context.Context, sf SettlingFile) error {
	settlingDuration := dw.config.SettlingTime

	// Check if file still exists
	_, err := os.Stat(sf.path)
	if err != nil {
		if os.IsNotExist(err) {
			dw.settlingFiles.Delete(sf.path)
			return fmt.Errorf("file disappeared while waiting for it to settle: %w", err)
		}
		dw.settlingFiles.Delete(sf.path)
		return fmt.Errorf("failed to stat file: %w", err)
	}

	// Check for changes
	if err := sf.checkForChange(); err != nil {
		dw.settlingFiles.Delete(sf.path)
		return fmt.Errorf("failed to check file for changes: %w", err)
	}

	// Update the settling file in the map
	dw.settlingFiles.Store(sf.path, &sf)

	// Check if the file has settled (sameCount * 100ms >= settling duration)
	settledTime := time.Duration(sf.sameCount) * 100 * time.Millisecond
	dw.logger.Debug("Checking file settling", zap.String("path", sf.path), zap.Int("sameCount", sf.sameCount), zap.Duration("settledTime", settledTime), zap.Duration("settlingDuration", settlingDuration))

	if settledTime >= settlingDuration {
		// File has settled, send to egress queue
		dw.logger.Debug("File has settled, sending to egress queue", zap.String("path", sf.path), zap.Int("sameCount", sf.sameCount))
		dw.settlingFiles.Delete(sf.path)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case dw.egressQueue <- sf:
			return nil
		}
	}

	dw.logger.Debug("File not settled yet", zap.String("path", sf.path), zap.Int("sameCount", sf.sameCount), zap.Duration("settlingDuration", settlingDuration))

	// File hasn't settled yet, re-add to delay queue for another 100ms
	dw.delayQueue.Add(&sf, 100*time.Millisecond)
	return nil
}

// Start begins watching for new files
func (dw *DirWatcher) Start(ctx context.Context) error {
	// Create a worker pool for event handling
	g, ctx := errgroup.WithContext(ctx)

	// Create a worker pool for egress file processing
	for i := 0; i < dw.egressWorkers; i++ {
		g.Go(func() error {
			for {
				select {
				case <-ctx.Done():
					return ctx.Err()
				case settlingFile, ok := <-dw.egressQueue:
					if !ok {
						return nil
					}
					if err := dw.processFile(ctx, settlingFile); err != nil {
						dw.logger.Error("Failed to process egress file",
							zap.String("file", settlingFile.path),
							zap.Int("sameCount", settlingFile.sameCount),
							zap.Duration("settlingTime", time.Since(settlingFile.startTime)),
							zap.Error(err))
					}
				}
			}
		})
	}

	// Start the delay queue
	dw.delayQueue.Start()

	// Process existing files
	g.Go(func() error {
		return dw.processExistingFiles(ctx)
	})

	// Start periodic processExistingFiles every 10 minutes
	dw.periodicTicker = time.NewTicker(10 * time.Minute)
	g.Go(func() error {
		defer dw.periodicTicker.Stop()
		for {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-dw.periodicTicker.C:
				dw.logger.Info("Running periodic processExistingFiles")
				if err := dw.processExistingFiles(ctx); err != nil {
					dw.logger.Error("Failed to process existing files periodically", zap.Error(err))
				}
			}
		}
	})

	// Watch for new files
	for range 2 {
		g.Go(func() error {
			return dw.handleEvents(ctx)
		})
	}

	// Start watching the root directory
	if err := dw.watcher.Add(dw.config.WatchDir); err != nil {
		return fmt.Errorf("failed to watch root directory: %w", err)
	}

	// Scan for existing subdirectories
	if err := dw.scanImmediateSubDirs(); err != nil {
		return fmt.Errorf("failed to scan subdirectories: %w", err)
	}

	dw.logger.Info("Started watching directory", zap.String("dir", dw.config.WatchDir))

	// Wait for context cancellation or error
	return g.Wait()
}

func (dw *DirWatcher) processExistingFiles(ctx context.Context) error {
	return filepath.Walk(dw.config.WatchDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		// Skip the root directory itself
		if path == dw.config.WatchDir {
			return nil
		}

		// Skip hidden files/directories unless they are metadata files or immediate children of watch dir
		if strings.HasPrefix(filepath.Base(path), ".") && !isMetadataFile(path) && filepath.Dir(path) != dw.config.WatchDir {
			if info.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}

		if info.IsDir() {
			// Load default metadata for directory
			if metadata := dw.loadDefaultMetadata(path); metadata != nil {
				dw.metadataMap.Store(path, metadata)
				dw.logger.Info("Loaded default metadata for directory",
					zap.String("dir", path),
					zap.Any("metadata", metadata))
			}
			return nil
		}

		// Skip metadata files
		if isMetadataFile(path) {
			return nil
		}

		// Add existing files to delay queue for settling monitoring
		if err := dw.addFileToDelayQueue(ctx, path); err != nil {
			dw.logger.Error("Failed to add existing file to delay queue",
				zap.String("file", path),
				zap.Error(err))
		}

		return nil
	})
}

func (dw *DirWatcher) handleEvents(ctx context.Context) error {
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case event, ok := <-dw.watcher.Events:
			if !ok {
				return nil
			}

			if isHidden(event.Name) && !isMetadataFile(event.Name) && filepath.Dir(event.Name) != dw.config.WatchDir {
				continue
			}

			// Handle metadata file changes first
			if isMetadataFile(event.Name) {
				dir := filepath.Dir(event.Name)
				// Wait a short time for the file write to complete
				time.Sleep(50 * time.Millisecond)
				if metadata := dw.loadDefaultMetadata(dir); metadata != nil {
					dw.metadataMap.Store(dir, metadata)
					dw.logger.Info("Updated default metadata for directory",
						zap.String("dir", dir),
						zap.Any("metadata", metadata))
				} else {
					dw.metadataMap.Delete(dir)
					dw.logger.Info("Removed default metadata for directory",
						zap.String("dir", dir))
				}
				continue
			}

			if event.Op&fsnotify.Create == fsnotify.Create {
				fi, err := os.Stat(event.Name)
				if err != nil {
					continue
				}

				if fi.IsDir() {
					dw.addWatch(event.Name)
					// Load default metadata for new directory
					if metadata := dw.loadDefaultMetadata(event.Name); metadata != nil {
						dw.metadataMap.Store(event.Name, metadata)
						dw.logger.Info("Loaded default metadata for new directory",
							zap.String("dir", event.Name),
							zap.Any("metadata", metadata))
					}
				} else {
					// Start monitoring the file for settling using delay queue
					if err := dw.addFileToDelayQueue(ctx, event.Name); err != nil {
						dw.logger.Error("Failed to add file to delay queue",
							zap.String("file", event.Name),
							zap.Error(err))
					}
				}
			}

			if event.Op&fsnotify.Remove == fsnotify.Remove {
				dw.removeWatch(event.Name)
				// Clean up metadata when directory is removed
				dw.metadataMap.Delete(event.Name)
			}
		case err, ok := <-dw.watcher.Errors:
			if !ok {
				return nil
			}
			dw.logger.Error("Watcher error", zap.Error(err))
		}
	}
}

func (dw *DirWatcher) processFile(ctx context.Context, sf SettlingFile) error {
	// Get file info for size check
	fi, err := os.Stat(sf.path)
	if err != nil {
		return fmt.Errorf("failed to stat file: %w", err)
	}

	if fi.Size() > dw.config.MaxFileSize {
		return fmt.Errorf("file exceeds maximum size limit: %d > %d", fi.Size(), dw.config.MaxFileSize)
	}

	// Find the data source directory (first directory under watch dir)
	relPath, err := filepath.Rel(dw.config.WatchDir, sf.path)
	if err != nil {
		return fmt.Errorf("failed to get relative path: %w", err)
	}
	dataSource := strings.Split(relPath, string(filepath.Separator))[0]

	// Create the result structure
	result := map[string]string{
		"dataSource": dataSource,
	}

	// Load default metadata from the file's directory or its parents
	if defaultMetadata := dw.loadDefaultMetadata(filepath.Dir(sf.path)); defaultMetadata != nil {
		metadataJSON, err := json.Marshal(defaultMetadata)
		if err != nil {
			return fmt.Errorf("failed to marshal metadata: %w", err)
		}
		result["metadata"] = string(metadataJSON)
	} else {
		// If no metadata, set empty object
		result["metadata"] = "{}"
	}

	if err := dw.handler.HandleFile(ctx, sf.path, result); err != nil {
		dw.delayQueue.Add(&sf, dw.config.RetryPeriod)
	}

	return nil
}

func (dw *DirWatcher) scanImmediateSubDirs() error {
	return filepath.Walk(dw.config.WatchDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		// Skip the root directory itself
		if path == dw.config.WatchDir {
			return nil
		}

		// Skip hidden directories unless they are immediate children of the watch dir
		if strings.HasPrefix(filepath.Base(path), ".") && filepath.Dir(path) != dw.config.WatchDir {
			if info.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}

		if info.IsDir() {
			dw.addWatch(path)
			// Load default metadata for directory
			if metadata := dw.loadDefaultMetadata(path); metadata != nil {
				dw.metadataMap.Store(path, metadata)
				dw.logger.Info("Loaded default metadata for directory",
					zap.String("dir", path),
					zap.Any("metadata", metadata))
			}
		}
		return nil
	})
}

func (dw *DirWatcher) addWatch(dir string) {
	if _, loaded := dw.watchList.LoadOrStore(dir, true); loaded {
		return
	}

	if err := dw.watcher.Add(dir); err != nil {
		dw.logger.Error("Failed to watch directory",
			zap.String("dir", dir),
			zap.Error(err))
		dw.watchList.Delete(dir)
		return
	}

	dw.logger.Info("Started watching directory", zap.String("dir", dir))
}

func (dw *DirWatcher) removeWatch(dir string) {
	if _, exists := dw.watchList.LoadAndDelete(dir); !exists {
		return
	}

	if err := dw.watcher.Remove(dir); err != nil {
		dw.logger.Error("Failed to remove watch from directory",
			zap.String("dir", dir),
			zap.Error(err))
		return
	}

	dw.logger.Info("Stopped watching directory", zap.String("dir", dir))
}

func isHidden(path string) bool {
	return strings.HasPrefix(filepath.Base(path), ".")
}

func isMetadataFile(path string) bool {
	base := filepath.Base(path)
	for _, metadataFile := range defaultMetadataFiles {
		if base == metadataFile {
			return true
		}
	}
	return false
}

// Stop stops the watcher and cleans up resources
func (dw *DirWatcher) Stop() error {
	// Stop the delay queue
	dw.delayQueue.Stop()

	// Stop the periodic ticker
	if dw.periodicTicker != nil {
		dw.periodicTicker.Stop()
	}

	// Close egress queue
	close(dw.egressQueue)

	return dw.watcher.Close()
}
