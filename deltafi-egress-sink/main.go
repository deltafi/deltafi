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
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"deltafi.org/deltafi_egress_sink/logger"
)

const (
	outputDir = "/data/deltafi/egress-sink"
)

var dropMetadata bool

type Metadata struct {
	Filename string `json:"filename"`
	Flow     string `json:"flow"`
}

func main() {
	dropMetadataEnv := os.Getenv("EGRESS_SINK_DROP_METADATA")
	dropMetadata = dropMetadataEnv == "true" || dropMetadataEnv == "yes"
	if dropMetadata {
		logger.Info("EGRESS_SINK_DROP_METADATA is enabled - metadata files will not be saved")
	}

	logger.Info("Starting server on :80")
	http.HandleFunc("/probe", func(w http.ResponseWriter, r *http.Request) {})
	http.HandleFunc("/blackhole", blackholeHandler)
	http.HandleFunc("/capture", captureHandler)
	http.HandleFunc("/", fileSinkHandler)
	err := http.ListenAndServe(":80", nil)
	if err != nil {
		logger.Error("Failed to start server: %v", err)
	}
}

func blackholeHandler(w http.ResponseWriter, r *http.Request) {
	// grab the form value before discarding the body
	latencyParam := r.FormValue("latency")

	// Consume and discard the request body
	_, err := io.Copy(io.Discard, r.Body)
	if err != nil {
		logger.Error("Error discarding request body: %v", err)
	}

	if latencyParam != "" {
		latency, err := time.ParseDuration(latencyParam + "s")
		if err != nil {
			logger.Error("Invalid latency value: %v", err)
			http.Error(w, "Invalid latency parameter", http.StatusBadRequest)
			return
		}
		logger.Info("Sleeping for %v", latency)
		time.Sleep(latency)
	}
	logger.Info("Processed blackhole request")
	w.Header().Set("Content-Length", "0")
	w.WriteHeader(http.StatusOK)
}

func captureHandler(w http.ResponseWriter, r *http.Request) {
	capturePath := filepath.Join(outputDir, "capture")
	if err := os.MkdirAll(capturePath, os.ModePerm); err != nil {
		logger.Error("Error creating capture directory: %v", err)
		http.Error(w, fmt.Sprintf("Error creating directory: %v", err), http.StatusInternalServerError)
		return
	}

	filename := fmt.Sprintf("%d.bin", time.Now().UnixNano())
	filePath := filepath.Join(capturePath, filename)

	if err := saveContent(filePath, r.Body); err != nil {
		logger.Error("Error saving captured content: %v", err)
		http.Error(w, fmt.Sprintf("Error saving content: %v", err), http.StatusInternalServerError)
		return
	}

	logger.Info("Captured file saved: %s", filePath)
	w.Header().Set("Content-Length", "0")
	w.WriteHeader(http.StatusOK)
}

func fileSinkHandler(w http.ResponseWriter, r *http.Request) {
	metadataJSON := r.Header.Get("DeltafiMetadata")
	if metadataJSON == "" {
		logger.Error("Missing HTTP header: DeltafiMetadata")
		http.Error(w, "Missing HTTP header: DeltafiMetadata", http.StatusBadRequest)
		return
	}

	var metadata Metadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		logger.Error("Invalid metadata JSON: %v", err)
		http.Error(w, "Invalid metadata JSON", http.StatusBadRequest)
		return
	}

	if metadata.Filename == "" || metadata.Flow == "" {
		logger.Error("Missing metadata keys")
		http.Error(w, "Missing metadata keys", http.StatusBadRequest)
		return
	}

	flowPath := filepath.Join(outputDir, metadata.Flow)
	if err := os.MkdirAll(flowPath, os.ModePerm); err != nil {
		logger.Error("Error creating directory: %v", err)
		http.Error(w, fmt.Sprintf("Error creating directory: %v", err), http.StatusInternalServerError)
		return
	}

	filePath := filepath.Join(flowPath, filepath.Base(metadata.Filename))

	// Save the content
	if err := saveContent(filePath, r.Body); err != nil {
		logger.Error("Error saving content: %v", err)
		http.Error(w, fmt.Sprintf("Error saving content: %v", err), http.StatusInternalServerError)
		return
	}

	// Save the metadata (unless dropMetadata global is true)
	if !dropMetadata {
		if err := saveMetadata(filePath, r.Method, metadataJSON); err != nil {
			logger.Error("Error saving metadata: %v", err)
			http.Error(w, fmt.Sprintf("Error saving metadata: %v", err), http.StatusInternalServerError)
			return
		}
	} else {
		logger.Info("Skipping metadata save due to DROP_METADATA environment variable")
	}

	logger.Info("File %s saved", filePath)
	w.Header().Set("Content-Length", "0")
	w.WriteHeader(http.StatusOK)
}

func saveContent(filePath string, body io.ReadCloser) error {
	file, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("error creating file: %w", err)
	}
	defer func(file *os.File) {
		err := file.Close()
		if err != nil {
			logger.Error("Error closing file: %v", err)
		}
	}(file)

	writer := bufio.NewWriterSize(file, 128*1024)
	if _, err := io.Copy(writer, body); err != nil {
		return fmt.Errorf("error writing to file: %w", err)
	}

	if err := writer.Flush(); err != nil {
		return fmt.Errorf("error flushing buffer: %w", err)
	}

	return nil
}

func saveMetadata(filePath, method, metadataJSON string) error {
	metadataPath := filePath + "." + method + ".metadata.json"
	if err := os.WriteFile(metadataPath, []byte(metadataJSON), os.ModePerm); err != nil {
		return fmt.Errorf("error writing metadata file: %w", err)
	}
	return nil
}
