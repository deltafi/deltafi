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

// ABOUTME: Entry point for the DeltaFi cluster monitor.
// ABOUTME: Periodically collects Kubernetes container metrics and sends them to Graphite.

package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	metricsv "k8s.io/metrics/pkg/client/clientset/versioned"
)

func main() {
	logLevel := slog.LevelInfo
	if os.Getenv("DEBUG") != "" {
		logLevel = slog.LevelDebug
	}
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	}))
	slog.SetDefault(logger)

	graphiteHost := envOrDefault("GRAPHITE_HOST", "deltafi-victoriametrics")
	graphitePort := envOrDefault("GRAPHITE_PORT", "2003")
	periodStr := envOrDefault("PERIOD", "9")
	nodeName := os.Getenv("NODE_NAME")

	period, err := strconv.Atoi(periodStr)
	if err != nil {
		slog.Error("invalid PERIOD", "value", periodStr, "error", err)
		os.Exit(1)
	}

	slog.Info("starting up", "node", nodeName)
	slog.Info("graphite target", "host", graphiteHost, "port", graphitePort)
	slog.Info("reporting period", "seconds", period)

	config, err := rest.InClusterConfig()
	if err != nil {
		slog.Error("failed to get in-cluster config", "error", err)
		os.Exit(1)
	}

	kubeClient, err := kubernetes.NewForConfig(config)
	if err != nil {
		slog.Error("failed to create kubernetes client", "error", err)
		os.Exit(1)
	}

	metricsClient, err := metricsv.NewForConfig(config)
	if err != nil {
		slog.Error("failed to create metrics client", "error", err)
		os.Exit(1)
	}

	collector := &Collector{
		kubeClient:    kubeClient,
		metricsClient: metricsClient,
	}

	graphite := &GraphiteClient{
		host: graphiteHost,
		port: graphitePort,
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGTERM, syscall.SIGINT)

	collect := func() {
		metrics, err := collector.Collect(ctx)
		if err != nil {
			slog.Error("collection failed", "error", err)
			return
		}
		slog.Debug("collected metrics", "count", len(metrics))
		if err := graphite.Send(metrics); err != nil {
			slog.Error("failed to send metrics", "error", err)
		}
	}

	collect()
	ticker := time.NewTicker(time.Duration(period) * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			collect()
		case sig := <-sigCh:
			slog.Warn("terminating the clustermonitor", "signal", sig.String())
			return
		}
	}
}

func envOrDefault(key, defaultValue string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultValue
}
