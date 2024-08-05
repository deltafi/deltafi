package main

import (
    "bufio"
    "encoding/json"
    "fmt"
    "io"
    "deltafi.org/deltafi_egress_sink/logger"
    "net/http"
    "os"
    "path/filepath"
    "time"
)

const outputDir = "/data/deltafi/egress-sink"

type Metadata struct {
    Filename string `json:"filename"`
    Flow     string `json:"flow"`
}

func main() {
    logger.Info("Starting server on :80")
    http.HandleFunc("/probe", func(w http.ResponseWriter, r *http.Request) {})
    http.HandleFunc("/blackhole", blackholeHandler)
    http.HandleFunc("/", fileSinkHandler)
    err := http.ListenAndServe(":80", nil)
    if err != nil {
        logger.Error("Failed to start server: %v", err)
    }
}

func blackholeHandler(w http.ResponseWriter, r *http.Request) {
    // Consume and discard the request body
    _, err := io.Copy(io.Discard, r.Body)
    if err != nil {
        logger.Error("Error discarding request body: %v", err)
    }
    defer func(Body io.ReadCloser) {
        err := Body.Close()
        if err != nil {
            logger.Error("Error closing request body: %v", err)
        }
    }(r.Body)

    latencyParam := r.FormValue("latency")
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
    w.WriteHeader(http.StatusOK)
}

func fileSinkHandler(w http.ResponseWriter, r *http.Request) {
    metadataJSON := r.Header.Get("DeltafiMetadata")
    if metadataJSON == "" {
        logger.Error("Missing metadata header")
        http.Error(w, "Missing metadata header", http.StatusBadRequest)
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

    if err := saveFile(metadata, r.Body, metadataJSON); err != nil {
        logger.Error("Error saving file: %v", err)
        http.Error(w, fmt.Sprintf("Error saving file: %v", err), http.StatusInternalServerError)
        return
    }
    w.WriteHeader(http.StatusOK)
}

func saveFile(metadata Metadata, body io.ReadCloser, metadataJSON string) error {
    defer func(body io.ReadCloser) {
        err := body.Close()
        if err != nil {
            logger.Error("Error closing body: %v", err)
        }
    }(body)

    flowPath := filepath.Join(outputDir, metadata.Flow)
    if err := os.MkdirAll(flowPath, os.ModePerm); err != nil {
        return fmt.Errorf("error creating directory: %w", err)
    }

    filePath := filepath.Join(flowPath, filepath.Base(metadata.Filename))
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

    metadataPath := filePath + ".metadata.json"
    if err := os.WriteFile(metadataPath, []byte(metadataJSON), os.ModePerm); err != nil {
        return fmt.Errorf("error writing metadata file: %w", err)
    }

    writer := bufio.NewWriterSize(file, 128 * 1024)
    if _, err := io.Copy(writer, body); err != nil {
        return fmt.Errorf("error writing to file: %w", err)
    }

    if err := writer.Flush(); err != nil {
        return fmt.Errorf("error flushing buffer: %w", err)
    }

    logger.Info("File %s saved", filePath)
    return nil
}
