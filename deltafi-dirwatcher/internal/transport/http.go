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
	"os"
	"path/filepath"
	"sync"
	"time"

	"go.uber.org/zap"
)

// FailedFile represents a file that failed to upload
type FailedFile struct {
	Path       string
	Metadata   map[string]string
	LastTry    time.Time
	RetryCount int
}

// HTTPClient handles file uploads to the specified endpoint
type HTTPClient struct {
	client      *http.Client
	endpoint    string
	logger      *zap.Logger
	retryPeriod time.Duration
	failedFiles sync.Map // map[string]*FailedFile
}

// NewHTTPClient creates a new HTTP client with the specified configuration
func NewHTTPClient(endpoint string, timeout time.Duration, retryPeriod time.Duration, logger *zap.Logger) *HTTPClient {
	return &HTTPClient{
		client: &http.Client{
			Timeout: timeout,
		},
		endpoint:    endpoint,
		logger:      logger,
		retryPeriod: retryPeriod,
	}
}

// HandleFile implements the FileHandler interface
func (c *HTTPClient) HandleFile(ctx context.Context, path string, metadata map[string]string) error {
	err := c.uploadFile(ctx, path, metadata)
	if err != nil {
		// Store failed file for retry
		c.failedFiles.Store(path, &FailedFile{
			Path:       path,
			Metadata:   metadata,
			LastTry:    time.Now(),
			RetryCount: 0,
		})
		c.logger.Error("File upload failed, will retry later",
			zap.String("file", path),
			zap.Error(err))
		return err
	}
	return nil
}

// StartRetryWorker starts a worker that periodically retries failed files
func (c *HTTPClient) StartRetryWorker(ctx context.Context) {
	ticker := time.NewTicker(c.retryPeriod)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			c.retryFailedFiles(ctx)
		}
	}
}

func (c *HTTPClient) retryFailedFiles(ctx context.Context) {
	c.failedFiles.Range(func(key, value interface{}) bool {
		path := key.(string)
		failedFile := value.(*FailedFile)

		// Check if enough time has passed since the last try
		if time.Since(failedFile.LastTry) < c.retryPeriod {
			return true
		}

		// Check if file still exists
		if _, err := os.Stat(path); os.IsNotExist(err) {
			c.failedFiles.Delete(path)
			c.logger.Info("File no longer exists, removing from retry queue",
				zap.String("file", path))
			return true
		}

		// Try to upload the file again
		err := c.uploadFile(ctx, failedFile.Path, failedFile.Metadata)
		if err != nil {
			failedFile.LastTry = time.Now()
			failedFile.RetryCount++
			c.logger.Error("Retry failed",
				zap.String("file", path),
				zap.Int("retryCount", failedFile.RetryCount),
				zap.Error(err))
		} else {
			c.failedFiles.Delete(path)
			c.logger.Info("Successfully uploaded file after retry",
				zap.String("file", path),
				zap.Int("retryCount", failedFile.RetryCount))
		}

		return true
	})
}

func (c *HTTPClient) uploadFile(ctx context.Context, path string, metadata map[string]string) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	// Get file info for size
	fileInfo, err := file.Stat()
	if err != nil {
		return fmt.Errorf("failed to get file info: %w", err)
	}
	fileSize := fileInfo.Size()

	pr, pw := io.Pipe()
	defer pr.Close()

	// Start file copy in a goroutine
	go func() {
		_, err := io.Copy(pw, file)
		if err != nil {
			c.logger.Error("error copying file", zap.Error(err))
		}
		pw.Close()
	}()

	req, err := http.NewRequestWithContext(ctx, "POST", c.endpoint, pr)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Filename", filepath.Base(path))
	req.Header.Set("X-User-Permissions", "Admin")
	req.Header.Set("X-User-Name", "deltafi-cli")

	// Add metadata headers
	for k, v := range metadata {
		req.Header.Set(k, v)
	}

	resp, err := c.client.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("unexpected status code: %d, body: %s", resp.StatusCode, string(body))
	}

	// Log successful upload with file size
	c.logger.Info("Successfully uploaded file",
		zap.String("file", path),
		zap.String("filename", filepath.Base(path)),
		zap.Int64("size_bytes", fileSize),
		zap.Any("metadata", metadata))

	// Delete the file after successful upload
	if err := os.Remove(path); err != nil {
		c.logger.Error("Failed to delete file after upload",
			zap.String("file", path),
			zap.Error(err))
	}

	return nil
}
