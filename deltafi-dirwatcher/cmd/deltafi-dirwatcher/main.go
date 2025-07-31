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
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"

	"deltafi.org/deltafi-dirwatcher/internal/transport"
	"deltafi.org/deltafi-dirwatcher/internal/watcher"
)

func main() {
	// Initialize logger
	logger, err := zap.NewProduction()
	if err != nil {
		log.Fatalf("Failed to create logger: %v", err)
	}
	defer logger.Sync()

	// Load configuration
	config, err := watcher.LoadConfig()
	if err != nil {
		logger.Fatal("Failed to load configuration", zap.Error(err))
	}

	// Create HTTP client
	httpClient := transport.NewHTTPClient(
		config.Endpoint,
		30*time.Second,
		time.Duration(config.RetryPeriod)*time.Second,
		logger,
	)

	// Create directory watcher
	dirWatcher, err := watcher.NewDirWatcher(config, httpClient, logger)
	if err != nil {
		logger.Fatal("Failed to create watcher", zap.Error(err))
	}

	// Setup context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Start retry worker
	go httpClient.StartRetryWorker(ctx)

	// Handle shutdown signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigChan
		logger.Info("Received shutdown signal", zap.String("signal", sig.String()))
		cancel()
	}()

	// Start the watcher
	if err := dirWatcher.Start(ctx); err != nil {
		logger.Fatal("Watcher error", zap.Error(err))
	}

	// Clean shutdown
	if err := dirWatcher.Stop(); err != nil {
		logger.Error("Error during shutdown", zap.Error(err))
	}
}
