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
// ABOUTME: Time-series graph command for visualizing VictoriaMetrics data
// ABOUTME: Supports predefined metrics and custom MetricsQL queries
package cmd

import (
	"fmt"
	"slices"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/spf13/cobra"
)

// dataSourceFilter specifies which data sources to include or exclude
type dataSourceFilter struct {
	include []string // If non-empty, only include these
	exclude []string // If non-empty, exclude these
}

// matchesSeries returns true if the series name matches the filter criteria
func (f dataSourceFilter) matchesSeries(name string) bool {
	// If include list is specified, series must be in the list
	if len(f.include) > 0 {
		return slices.Contains(f.include, name)
	}

	// If exclude list is specified, series must not be in the list
	if len(f.exclude) > 0 {
		if slices.Contains(f.exclude, name) {
			return false
		}
	}

	return true
}

var graphCmd = &cobra.Command{
	Use:   "graph",
	Short: "Display time-series metrics graphs",
	Long: `Display interactive time-series graphs from VictoriaMetrics data.

Supports predefined metrics and custom MetricsQL queries with interactive
time controls and auto-refresh.

Examples:
  deltafi graph                            # Open metric selection modal
  deltafi graph --metric bytes             # Bytes ingested per data source
  deltafi graph --metric files --from 4h   # Files ingested (last 4h)
  deltafi graph --query '{__name__="stats_counts.files_from_source"}'
  deltafi graph --query '{__name__="my.metric"}' --title "My Metric"

Interactive controls:
  c      Change metric
  +/-    Zoom time range (double/halve)
  ←/→    Pan time window (back/forward)
  x      Return to now (reset pan)
  1-0    Toggle series 1-10 visibility
  f      Open filter modal
  a/n    Show/hide all series
  r      Force refresh
  q/Esc  Quit`,
	GroupID:      "metrics",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		metricKey, _ := cmd.Flags().GetString("metric")
		customQuery, _ := cmd.Flags().GetString("query")
		customTitle, _ := cmd.Flags().GetString("title")
		debug, _ := cmd.Flags().GetBool("debug")
		raw, _ := cmd.Flags().GetBool("raw")

		fromStr, _ := cmd.Flags().GetString("from")
		from, err := parseDuration(fromStr)
		if err != nil {
			return fmt.Errorf("invalid --from value: %w", err)
		}

		intervalStr, _ := cmd.Flags().GetString("interval")
		interval, err := parseDuration(intervalStr)
		if err != nil {
			return fmt.Errorf("invalid --interval value: %w", err)
		}

		filter, err := parseDataSourceFilter(cmd)
		if err != nil {
			return err
		}

		chartStyle := parseChartStyle(cmd)
		hideLegend, _ := cmd.Flags().GetBool("hide-legend")

		// If custom query has a title, register it as a metric
		if customQuery != "" && customTitle != "" {
			metric := api.AddCustomMetric("custom-query", customTitle, customQuery)
			if debug || raw {
				return runDebugOrRaw(metric, "", from, debug, raw)
			}
			return runGraph(metric, "", from, interval, filter, chartStyle, hideLegend)
		}

		// Handle --debug and --raw flags
		if debug || raw {
			var metric *api.MetricDefinition
			if metricKey != "" {
				m, ok := api.GetMetricByKey(metricKey)
				if !ok {
					return fmt.Errorf("unknown metric: %s\nAvailable metrics: %s", metricKey, api.GetAvailableMetricKeys())
				}
				metric = m
			} else if customQuery == "" {
				return fmt.Errorf("--debug and --raw require --metric or --query")
			}
			return runDebugOrRaw(metric, customQuery, from, debug, raw)
		}

		// Custom query without title
		if customQuery != "" {
			return runGraph(nil, customQuery, from, interval, filter, chartStyle, hideLegend)
		}

		// If metric specified, look it up
		if metricKey != "" {
			metric, ok := api.GetMetricByKey(metricKey)
			if !ok {
				return fmt.Errorf("unknown metric: %s\nAvailable metrics: %s", metricKey, api.GetAvailableMetricKeys())
			}
			return runGraph(metric, "", from, interval, filter, chartStyle, hideLegend)
		}

		// No metric specified - show selection modal
		return runGraph(nil, "", from, interval, filter, chartStyle, hideLegend)
	},
}

func runDebugOrRaw(metric *api.MetricDefinition, customQuery string, from time.Duration, debug, raw bool) error {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return clientError(err)
	}

	metricsClient := api.NewMetricsClient(client)

	if debug {
		url := metricsClient.BuildQueryURL(metric, customQuery, from, 0, 100)
		fmt.Println(url)
	}

	if raw {
		data, err := metricsClient.QueryRaw(metric, customQuery, from, 0, 100)
		if err != nil {
			return fmt.Errorf("query failed: %w", err)
		}
		fmt.Println(string(data))
	}

	return nil
}

func runGraph(metric *api.MetricDefinition, customQuery string, from, interval time.Duration, filter dataSourceFilter, chartStyle components.ChartStyle, hideLegend bool) error {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return clientError(err)
	}

	metricsClient := api.NewMetricsClient(client)

	p := tea.NewProgram(
		newGraphModel(metricsClient, metric, customQuery, from, interval, filter, chartStyle, !hideLegend),
		tea.WithAltScreen(),
	)

	if _, err := p.Run(); err != nil {
		return fmt.Errorf("failed to run graph: %w", err)
	}

	return nil
}

// parseDuration parses a duration string like "1h", "30m", "1d", "1w"
// Extends Go's time.ParseDuration to support days (d) and weeks (w)
func parseDuration(s string) (time.Duration, error) {
	if len(s) == 0 {
		return 0, fmt.Errorf("empty duration string")
	}

	// Check for week suffix
	if s[len(s)-1] == 'w' {
		numStr := s[:len(s)-1]
		var weeks float64
		if _, err := fmt.Sscanf(numStr, "%f", &weeks); err != nil {
			return 0, fmt.Errorf("invalid week duration: %s", s)
		}
		return time.Duration(weeks * 7 * 24 * float64(time.Hour)), nil
	}

	// Check for day suffix
	if s[len(s)-1] == 'd' {
		numStr := s[:len(s)-1]
		var days float64
		if _, err := fmt.Sscanf(numStr, "%f", &days); err != nil {
			return 0, fmt.Errorf("invalid day duration: %s", s)
		}
		return time.Duration(days * 24 * float64(time.Hour)), nil
	}

	return time.ParseDuration(s)
}

// parseDataSourceFilter extracts the data source filter from command flags
func parseDataSourceFilter(cmd *cobra.Command) (dataSourceFilter, error) {
	includeStr, _ := cmd.Flags().GetString("data-source")
	excludeStr, _ := cmd.Flags().GetString("not-data-source")

	if includeStr != "" && excludeStr != "" {
		return dataSourceFilter{}, fmt.Errorf("--data-source and --not-data-source are mutually exclusive")
	}

	var filter dataSourceFilter
	if includeStr != "" {
		filter.include = splitAndTrim(includeStr)
	}
	if excludeStr != "" {
		filter.exclude = splitAndTrim(excludeStr)
	}

	return filter, nil
}

// splitAndTrim splits a comma-separated string and trims whitespace
func splitAndTrim(s string) []string {
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}

// parseChartStyle extracts the chart style from command flags
func parseChartStyle(cmd *cobra.Command) components.ChartStyle {
	useLine, _ := cmd.Flags().GetBool("line")
	if useLine {
		return components.ChartStyleLine
	}
	return components.ChartStyleBraille
}

func init() {
	graphCmd.Flags().StringP("metric", "m", "", "Metric to display (e.g., bytes, files, node-cpu)")
	graphCmd.Flags().StringP("query", "q", "", "Custom MetricsQL query")
	graphCmd.Flags().StringP("title", "t", "", "Title for custom query (adds it to the metric list)")
	graphCmd.Flags().StringP("from", "f", "1h", "Time range to display (e.g., 1h, 30m, 1d, 1w)")
	graphCmd.Flags().StringP("interval", "i", "30s", "Auto-refresh interval (e.g., 10s, 1m); 0 to disable")
	graphCmd.Flags().StringP("data-source", "d", "", "Only show specified data sources (comma-separated)")
	graphCmd.Flags().StringP("not-data-source", "D", "", "Exclude specified data sources (comma-separated)")
	graphCmd.Flags().Bool("line", false, "Use line characters for chart (default: braille)")
	graphCmd.Flags().Bool("hide-legend", false, "Start with the legend hidden (toggle with 'l')")
	graphCmd.Flags().Bool("debug", false, "Print the query URL to stdout")
	graphCmd.Flags().Bool("raw", false, "Print raw JSON response to stdout instead of graphing")

	// Tab completion for --metric flag
	graphCmd.RegisterFlagCompletionFunc("metric", func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		metrics := api.GetAllMetrics()
		completions := make([]string, len(metrics))
		for i, m := range metrics {
			completions[i] = fmt.Sprintf("%s\t%s", m.Key, m.Title)
		}
		return completions, cobra.ShellCompDirectiveNoFileComp
	})

	rootCmd.AddCommand(graphCmd)
}
