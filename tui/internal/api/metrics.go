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
// ABOUTME: Client for querying time-series metrics via deltafi-core's
// ABOUTME: /api/v2/timeseries endpoint (proxied to VictoriaMetrics)
package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"
)

// TimeSeries represents a single named time series with data points
type TimeSeries struct {
	Name       string
	DataPoints []DataPoint
	Visible    bool
}

// DataPoint represents a single timestamp/value pair
type DataPoint struct {
	Timestamp time.Time
	Value     float64
	IsNull    bool
}

// MetricDefinition contains all information about a predefined metric
type MetricDefinition struct {
	Key      string // CLI flag value (e.g., "bytes", "files-in")
	Title    string // Display title
	Query    string // MetricsQL query expression (use {step} placeholder for counter intervals)
	LabelKey string // Label to use for series name (empty = use Alias or metric name)
	Alias    string // Fixed alias for single-series metrics
}

// metricRegistry is the single source of truth for all predefined metrics
// Queries use MetricsQL/PromQL syntax compatible with VictoriaMetrics
// StatsD counter metrics use sum_over_time() with {step} placeholder to aggregate per-flush values within each step window
var metricRegistry = []MetricDefinition{
	// Data ingestion metrics (StatsD per-flush counters - use sum_over_time to aggregate flush windows)
	{"bytes", "Bytes Ingested by Data Source", `sum_over_time({__name__="stats_counts.bytes_from_source"}[{step}])`, "dataSource", ""},
	{"files", "Files Ingested by Data Source", `sum_over_time({__name__="stats_counts.files_from_source"}[{step}])`, "dataSource", ""},

	// Egress metrics (StatsD per-flush counters)
	{"egress-bytes-source", "Bytes Egressed by Data Source", `sum_over_time({__name__="stats_counts.bytes_to_sink"}[{step}])`, "dataSource", ""},
	{"egress-files-source", "Files Egressed by Data Source", `sum_over_time({__name__="stats_counts.files_to_sink"}[{step}])`, "dataSource", ""},
	{"egress-bytes-sink", "Bytes Egressed by Data Sink", `sum by (dataSink) (sum_over_time({__name__="stats_counts.bytes_to_sink"}[{step}]))`, "dataSink", ""},
	{"egress-files-sink", "Files Egressed by Data Sink", `sum by (dataSink) (sum_over_time({__name__="stats_counts.files_to_sink"}[{step}]))`, "dataSink", ""},

	// Aggregate totals (StatsD per-flush counters, single series)
	{"bytes-in", "Total Bytes In", `sum(sum_over_time({__name__="stats_counts.bytes_from_source"}[{step}]))`, "", "Bytes In"},
	{"bytes-out", "Total Bytes Out", `sum(sum_over_time({__name__="stats_counts.bytes_to_sink"}[{step}]))`, "", "Bytes Out"},
	{"files-in", "Total Files In", `sum(sum_over_time({__name__="stats_counts.files_from_source"}[{step}]))`, "", "Files In"},
	{"files-out", "Total Files Out", `sum(sum_over_time({__name__="stats_counts.files_to_sink"}[{step}]))`, "", "Files Out"},

	// Error and filtering metrics (StatsD per-flush counters)
	{"files-errored", "Files Errored by Data Source", `sum_over_time({__name__="stats_counts.files_errored"}[{step}])`, "dataSource", ""},
	{"files-filtered", "Files Filtered by Data Source", `sum_over_time({__name__="stats_counts.files_filtered"}[{step}])`, "dataSource", ""},
	{"files-dropped", "Files Dropped by Data Source", `sum_over_time({__name__="stats_counts.files_dropped"}[{step}])`, "dataSource", ""},
	{"files-retried", "Files Retried by Data Source", `sum_over_time({__name__="stats_counts.files_auto_resumed"}[{step}])`, "dataSource", ""},

	// DeltaFile state metrics (gauges - no increase needed)
	{"total-delta-files", "Total Delta Files", `{__name__="gauge.deltafile.totalCount"}`, "", "Total DeltaFiles"},
	{"in-flight-files", "In-Flight Files", `{__name__="gauge.deltafile.inFlightCount"}`, "", "In-Flight Files"},
	{"in-flight-bytes", "In-Flight Bytes", `{__name__="gauge.deltafile.inFlightBytes"}`, "", "In-Flight Bytes"},
	{"cold-queued-files", "Cold-Queued Files", `{__name__="gauge.deltafile.coldQueuedCount"}`, "", "Cold Queued Files"},
	{"paused-files", "Paused Files", `{__name__="gauge.deltafile.pausedCount"}`, "", "Paused Files"},

	// Action queue and execution metrics
	{"queue-depth", "Action Queue Depth", `{__name__="gauge.action_queue.queue_size"}`, "queue_name", ""},
	{"action-exec-count", "Action Execution Count", `sum by (source) (sum_over_time({__name__="stats_counts.action_execution"}[{step}]))`, "source", ""},
	{"action-exec-time", "Action Execution Time (ms)", `sum by (source) (sum_over_time({__name__="stats_counts.action_execution_time_ms", action!="ingress"}[{step}]))`, "source", ""},
	{"exec-time-source", "Total Execution Time by Data Source (ms)", `sum by (dataSource) (sum_over_time({__name__="stats_counts.execution_time_ms"}[{step}]))`, "dataSource", ""},

	// Retention metrics (StatsD per-flush counters)
	{"files-deleted", "Files Deleted by Policy", `sum_over_time({__name__="stats_counts.deleted.files"}[{step}])`, "policy", ""},
	{"bytes-deleted", "Bytes Deleted by Policy", `sum_over_time({__name__="stats_counts.deleted.bytes"}[{step}])`, "policy", ""},

	// Infrastructure metrics (gauges)
	{"disk-usage", "Disk Usage", `{__name__="gauge.node.disk.usage"}`, "hostname", ""},
	{"node-cpu", "Node CPU Usage", `{__name__="gauge.node.cpu.usage"}`, "hostname", ""},
	{"node-memory", "Node Memory Usage", `{__name__="gauge.node.memory.usage"}`, "hostname", ""},
	{"app-cpu", "App CPU Usage", `sum by (app) ({__name__="gauge.app.cpu"})`, "app", ""},
	{"app-memory", "App Memory Usage", `sum by (app) ({__name__="gauge.app.memory"})`, "app", ""},
	{"plugin-cpu", "Plugin CPU Usage", `sum by (namespace) ({__name__="gauge.app.cpu", namespace!=""})`, "namespace", ""},
	{"plugin-memory", "Plugin Memory Usage", `sum by (namespace) ({__name__="gauge.app.memory", namespace!=""})`, "namespace", ""},
}

// metricByKey provides O(1) lookup by CLI key
var metricByKey = func() map[string]*MetricDefinition {
	m := make(map[string]*MetricDefinition, len(metricRegistry))
	for i := range metricRegistry {
		m[metricRegistry[i].Key] = &metricRegistry[i]
	}
	return m
}()

// GetAllMetrics returns the list of all predefined metrics
func GetAllMetrics() []MetricDefinition {
	return metricRegistry
}

// GetMetricByKey looks up a metric definition by its CLI key
func GetMetricByKey(key string) (*MetricDefinition, bool) {
	m, ok := metricByKey[key]
	return m, ok
}

// AddCustomMetric appends a user-defined metric to the registry
func AddCustomMetric(key, title, query string) *MetricDefinition {
	metric := MetricDefinition{
		Key:   key,
		Title: title,
		Query: query,
	}
	metricRegistry = append(metricRegistry, metric)
	metricByKey[key] = &metricRegistry[len(metricRegistry)-1]
	return &metricRegistry[len(metricRegistry)-1]
}

// GetAvailableMetricKeys returns a comma-separated list of available metric keys
func GetAvailableMetricKeys() string {
	keys := make([]string, len(metricRegistry))
	for i, m := range metricRegistry {
		keys[i] = m.Key
	}
	return strings.Join(keys, ", ")
}

// prometheusResponse represents the JSON response format from VictoriaMetrics query_range API
type prometheusResponse struct {
	Status string `json:"status"`
	Data   struct {
		ResultType string             `json:"resultType"`
		Result     []prometheusResult `json:"result"`
	} `json:"data"`
	Error     string `json:"error,omitempty"`
	ErrorType string `json:"errorType,omitempty"`
}

// prometheusResult represents a single time series in the Prometheus response
type prometheusResult struct {
	Metric map[string]string `json:"metric"`
	Values [][]any           `json:"values"` // [[timestamp, "value"], ...]
}

// MetricsClient queries VictoriaMetrics through the deltafi-core REST API
type MetricsClient struct {
	client *Client
}

// NewMetricsClient creates a metrics client that routes queries through deltafi-core
func NewMetricsClient(client *Client) *MetricsClient {
	return &MetricsClient{
		client: client,
	}
}

// BuildQueryURL constructs the full URL for a metric query without executing it
func (m *MetricsClient) BuildQueryURL(metric *MetricDefinition, customQuery string, from, until time.Duration, maxDataPoints int) string {
	now := time.Now()
	end := now.Add(-until)
	start := now.Add(-from)
	step := calculateStep(from-until, maxDataPoints)

	var query string
	if metric != nil {
		query = strings.ReplaceAll(metric.Query, "{step}", step)
	} else {
		query = customQuery
	}

	return fmt.Sprintf("%s/api/v2/timeseries?query=%s&start=%d&end=%d&step=%s",
		m.client.GetBaseURL(),
		url.QueryEscape(query),
		start.Unix(),
		end.Unix(),
		step,
	)
}

// QueryRaw executes a query and returns the raw JSON response bytes
func (m *MetricsClient) QueryRaw(metric *MetricDefinition, customQuery string, from, until time.Duration, maxDataPoints int) ([]byte, error) {
	queryURL := m.BuildQueryURL(metric, customQuery, from, until, maxDataPoints)

	req, err := http.NewRequest("GET", queryURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to make request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	var buf bytes.Buffer
	if _, err := buf.ReadFrom(resp.Body); err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	return buf.Bytes(), nil
}

// QueryRange executes a MetricsQL query with explicit from and until offsets.
// Both from and until are durations relative to now (positive values mean "ago").
// For example, from=2h, until=1h queries from 2 hours ago to 1 hour ago.
func (m *MetricsClient) QueryRange(query string, from, until time.Duration, maxDataPoints int) ([]TimeSeries, error) {
	now := time.Now()
	end := now.Add(-until)
	start := now.Add(-from)

	// Calculate step based on time range and desired data points
	step := calculateStep(from-until, maxDataPoints)

	queryURL := fmt.Sprintf("%s/api/v2/timeseries?query=%s&start=%d&end=%d&step=%s",
		m.client.GetBaseURL(),
		url.QueryEscape(query),
		start.Unix(),
		end.Unix(),
		step,
	)

	req, err := http.NewRequest("GET", queryURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to make request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	var promResp prometheusResponse
	if err := json.NewDecoder(resp.Body).Decode(&promResp); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	if promResp.Status != "success" {
		return nil, fmt.Errorf("query failed: %s - %s", promResp.ErrorType, promResp.Error)
	}

	return parsePrometheusResponse(promResp, "", ""), nil
}

// QueryMetric executes a query for a predefined metric, replacing {step} placeholder
func (m *MetricsClient) QueryMetric(metric *MetricDefinition, from, until time.Duration, maxDataPoints int) ([]TimeSeries, error) {
	now := time.Now()
	end := now.Add(-until)
	start := now.Add(-from)

	// Calculate step based on time range and desired data points
	step := calculateStep(from-until, maxDataPoints)

	// Replace {step} placeholder in query with actual step value
	query := strings.ReplaceAll(metric.Query, "{step}", step)

	queryURL := fmt.Sprintf("%s/api/v2/timeseries?query=%s&start=%d&end=%d&step=%s",
		m.client.GetBaseURL(),
		url.QueryEscape(query),
		start.Unix(),
		end.Unix(),
		step,
	)

	req, err := http.NewRequest("GET", queryURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to make request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	var promResp prometheusResponse
	if err := json.NewDecoder(resp.Body).Decode(&promResp); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	if promResp.Status != "success" {
		return nil, fmt.Errorf("query failed: %s - %s", promResp.ErrorType, promResp.Error)
	}

	return parsePrometheusResponse(promResp, metric.LabelKey, metric.Alias), nil
}

// calculateStep determines the query step based on time range and desired data points
func calculateStep(timeRange time.Duration, maxDataPoints int) string {
	if maxDataPoints < 10 {
		maxDataPoints = 10
	}

	stepSeconds := max(int(timeRange.Seconds())/maxDataPoints, 10)

	// Round to nice intervals
	switch {
	case stepSeconds < 15:
		return "10s"
	case stepSeconds < 45:
		return "30s"
	case stepSeconds < 90:
		return "1m"
	case stepSeconds < 180:
		return "2m"
	case stepSeconds < 450:
		return "5m"
	case stepSeconds < 900:
		return "10m"
	case stepSeconds < 1800:
		return "15m"
	case stepSeconds < 3600:
		return "30m"
	case stepSeconds < 7200:
		return "1h"
	case stepSeconds < 14400:
		return "2h"
	case stepSeconds < 43200:
		return "6h"
	default:
		return "12h"
	}
}

// parsePrometheusResponse converts the Prometheus JSON response to TimeSeries.
// Results that resolve to the same series name are merged by summing values at each timestamp.
func parsePrometheusResponse(resp prometheusResponse, labelKey, alias string) []TimeSeries {
	// Use ordered tracking to preserve insertion order while merging by name
	nameOrder := make([]string, 0, len(resp.Data.Result))
	seriesByName := make(map[string]*TimeSeries, len(resp.Data.Result))

	for _, series := range resp.Data.Result {
		name := extractSeriesName(series.Metric, labelKey, alias)

		existing, exists := seriesByName[name]
		if !exists {
			ts := &TimeSeries{
				Name:       name,
				DataPoints: make([]DataPoint, 0, len(series.Values)),
				Visible:    true,
			}
			seriesByName[name] = ts
			nameOrder = append(nameOrder, name)
			existing = ts

			for _, val := range series.Values {
				existing.DataPoints = append(existing.DataPoints, parseDataPoint(val))
			}
		} else {
			// Merge by summing values at matching timestamps
			pointsByTime := make(map[int64]int, len(existing.DataPoints))
			for i, dp := range existing.DataPoints {
				pointsByTime[dp.Timestamp.Unix()] = i
			}
			for _, val := range series.Values {
				dp := parseDataPoint(val)
				if idx, ok := pointsByTime[dp.Timestamp.Unix()]; ok && !dp.IsNull {
					if existing.DataPoints[idx].IsNull {
						existing.DataPoints[idx] = dp
					} else {
						existing.DataPoints[idx].Value += dp.Value
					}
				} else {
					pointsByTime[dp.Timestamp.Unix()] = len(existing.DataPoints)
					existing.DataPoints = append(existing.DataPoints, dp)
				}
			}
		}
	}

	result := make([]TimeSeries, 0, len(nameOrder))
	for _, name := range nameOrder {
		result = append(result, *seriesByName[name])
	}
	return result
}

// parseDataPoint parses a single [timestamp, "value"] pair from a Prometheus response
func parseDataPoint(val []any) DataPoint {
	if len(val) != 2 {
		return DataPoint{IsNull: true}
	}

	point := DataPoint{IsNull: true}

	if timestamp, ok := val[0].(float64); ok {
		point.Timestamp = time.Unix(int64(timestamp), 0)
	}

	if valStr, ok := val[1].(string); ok {
		if v, err := strconv.ParseFloat(valStr, 64); err == nil {
			point.Value = v
			point.IsNull = false
		}
	}

	return point
}

// extractSeriesName determines the display name for a series
func extractSeriesName(metric map[string]string, labelKey, alias string) string {
	// If a fixed alias is provided, use it
	if alias != "" {
		return alias
	}

	// If a label key is specified, try to extract that label
	if labelKey != "" {
		if val, ok := metric[labelKey]; ok && val != "" {
			return val
		}
	}

	// Fall back to __name__ or build from all labels
	if name, ok := metric["__name__"]; ok {
		return name
	}

	// Build a string from all labels (sorted for deterministic output)
	var keys []string
	for k := range metric {
		if k != "__name__" {
			keys = append(keys, k)
		}
	}
	sort.Strings(keys)
	var parts []string
	for _, k := range keys {
		parts = append(parts, fmt.Sprintf("%s=%s", k, metric[k]))
	}
	if len(parts) > 0 {
		return strings.Join(parts, ", ")
	}

	return "unknown"
}
