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
// ABOUTME: Time-series metrics MCP tools for querying VictoriaMetrics data
// ABOUTME: Provides tools for predefined and custom metric queries

package tools

import (
	"context"
	"fmt"
	"time"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_available_metrics",
			mcp.WithDescription("List all predefined metric keys that can be used with query_metrics"),
		),
		Handler: listAvailableMetrics,
		Tags:    []string{"metrics"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("query_metrics",
			mcp.WithDescription("Query time-series metrics data. Use list_available_metrics to see available predefined metric keys."),
			mcp.WithString("metric_key", mcp.Required(), mcp.Description("Predefined metric key (e.g., 'bytes', 'files', 'node-cpu', 'queue-depth'). Use list_available_metrics to see all options.")),
			mcp.WithString("from", mcp.Description("How far back to query (e.g., '1h', '6h', '24h', '7d'). Default: '1h'")),
			mcp.WithNumber("max_data_points", mcp.Description("Maximum data points to return (default: 100)")),
		),
		Handler: queryMetrics,
		Tags:    []string{"metrics"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("query_custom_metrics",
			mcp.WithDescription("Execute a custom MetricsQL/PromQL query against VictoriaMetrics"),
			mcp.WithString("query", mcp.Required(), mcp.Description("MetricsQL query expression")),
			mcp.WithString("from", mcp.Description("How far back to query (e.g., '1h', '6h', '24h', '7d'). Default: '1h'")),
			mcp.WithNumber("max_data_points", mcp.Description("Maximum data points to return (default: 100)")),
		),
		Handler: queryCustomMetrics,
		Tags:    []string{"metrics"},
	})
}

func listAvailableMetrics(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	metrics := api.GetAllMetrics()

	type MetricInfo struct {
		Key   string `json:"key"`
		Title string `json:"title"`
	}

	result := make([]MetricInfo, 0, len(metrics))
	for _, m := range metrics {
		result = append(result, MetricInfo{
			Key:   m.Key,
			Title: m.Title,
		})
	}

	return registry.ResultFromJSON(result)
}

func queryMetrics(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	metricKey, err := req.RequireString("metric_key")
	if err != nil {
		return registry.ErrorResult("metric_key parameter is required"), nil
	}

	metric, ok := api.GetMetricByKey(metricKey)
	if !ok {
		return registry.ErrorResult(fmt.Sprintf("unknown metric key: %s. Use list_available_metrics to see available keys.", metricKey)), nil
	}

	from := parseDurationOrDefault(req, "from", time.Hour)
	maxDataPoints := 100
	if mdp, err := req.RequireFloat("max_data_points"); err == nil {
		maxDataPoints = int(mdp)
	}

	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	mc := api.NewMetricsClient(client)
	series, err := mc.QueryMetric(metric, from, 0, maxDataPoints)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(formatTimeSeries(metric.Title, series))
}

func queryCustomMetrics(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	query, err := req.RequireString("query")
	if err != nil {
		return registry.ErrorResult("query parameter is required"), nil
	}

	from := parseDurationOrDefault(req, "from", time.Hour)
	maxDataPoints := 100
	if mdp, err := req.RequireFloat("max_data_points"); err == nil {
		maxDataPoints = int(mdp)
	}

	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	mc := api.NewMetricsClient(client)
	series, err := mc.QueryRange(query, from, 0, maxDataPoints)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(formatTimeSeries("Custom Query", series))
}

func parseDurationOrDefault(req mcp.CallToolRequest, param string, defaultVal time.Duration) time.Duration {
	s, err := req.RequireString(param)
	if err != nil || s == "" {
		return defaultVal
	}

	// Try standard Go duration first (handles "2h30m", "1h", "30s", etc.)
	if d, err := time.ParseDuration(s); err == nil {
		return d
	}

	// Support "d" shorthand (not in Go's time.ParseDuration)
	if len(s) > 1 && s[len(s)-1] == 'd' {
		var num float64
		if _, err := fmt.Sscanf(s[:len(s)-1], "%f", &num); err == nil {
			return time.Duration(num * float64(24*time.Hour))
		}
	}

	return defaultVal
}

type timeSeriesResult struct {
	Title  string       `json:"title"`
	Series []seriesData `json:"series"`
}

type seriesData struct {
	Name       string      `json:"name"`
	DataPoints []dataPoint `json:"data_points"`
}

type dataPoint struct {
	Timestamp string  `json:"timestamp"`
	Value     float64 `json:"value"`
}

func formatTimeSeries(title string, series []api.TimeSeries) timeSeriesResult {
	result := timeSeriesResult{
		Title:  title,
		Series: make([]seriesData, 0, len(series)),
	}

	for _, s := range series {
		sd := seriesData{
			Name:       s.Name,
			DataPoints: make([]dataPoint, 0, len(s.DataPoints)),
		}
		for _, dp := range s.DataPoints {
			if !dp.IsNull {
				sd.DataPoints = append(sd.DataPoints, dataPoint{
					Timestamp: dp.Timestamp.Format(time.RFC3339),
					Value:     dp.Value,
				})
			}
		}
		result.Series = append(result.Series, sd)
	}

	return result
}
