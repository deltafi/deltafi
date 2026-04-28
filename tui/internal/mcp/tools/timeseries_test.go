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
// ABOUTME: Tests for time-series metrics MCP tool duration parsing
// ABOUTME: Verifies that duration shorthand values are correctly converted

package tools

import (
	"testing"
	"time"

	"github.com/deltafi/tui/internal/api"
)

func TestParseDurationOrDefault(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected time.Duration
	}{
		{"hours", "1h", time.Hour},
		{"multiple hours", "6h", 6 * time.Hour},
		{"days", "7d", 7 * 24 * time.Hour},
		{"minutes", "30m", 30 * time.Minute},
		{"seconds", "60s", 60 * time.Second},
		{"go duration", "2h30m", 2*time.Hour + 30*time.Minute},
		{"empty string uses default", "", 5 * time.Minute},
		{"invalid uses default", "bogus", 5 * time.Minute},
	}

	defaultVal := 5 * time.Minute
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			args := map[string]any{}
			if tt.input != "" {
				args["from"] = tt.input
			}
			req := newCallToolRequest(args)
			got := parseDurationOrDefault(req, "from", defaultVal)
			if got != tt.expected {
				t.Errorf("parseDurationOrDefault(%q) = %v, want %v", tt.input, got, tt.expected)
			}
		})
	}
}

func TestFormatTimeSeries_FiltersNullPoints(t *testing.T) {
	series := []api.TimeSeries{
		{
			Name: "test",
			DataPoints: []api.DataPoint{
				{Timestamp: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC), Value: 42.0, IsNull: false},
				{Timestamp: time.Date(2026, 1, 1, 0, 1, 0, 0, time.UTC), Value: 0, IsNull: true},
				{Timestamp: time.Date(2026, 1, 1, 0, 2, 0, 0, time.UTC), Value: 99.0, IsNull: false},
			},
			Visible: true,
		},
	}

	result := formatTimeSeries("Test Metric", series)

	if result.Title != "Test Metric" {
		t.Errorf("expected title 'Test Metric', got %q", result.Title)
	}
	if len(result.Series) != 1 {
		t.Fatalf("expected 1 series, got %d", len(result.Series))
	}
	if len(result.Series[0].DataPoints) != 2 {
		t.Errorf("expected 2 non-null data points, got %d", len(result.Series[0].DataPoints))
	}
	if result.Series[0].DataPoints[0].Value != 42.0 {
		t.Errorf("expected first value 42.0, got %f", result.Series[0].DataPoints[0].Value)
	}
	if result.Series[0].DataPoints[1].Value != 99.0 {
		t.Errorf("expected second value 99.0, got %f", result.Series[0].DataPoints[1].Value)
	}
}
