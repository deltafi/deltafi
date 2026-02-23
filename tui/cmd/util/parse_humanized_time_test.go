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

// ABOUTME: Tests for humanized time parsing functions.
// ABOUTME: Covers simple keywords, relative expressions, and standard date formats.
package util

import (
	"testing"
	"time"
)

func TestParseHumanizedTime_SimpleKeywords(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		validate func(t *testing.T, result time.Time, now time.Time)
	}{
		{
			name:  "now",
			input: "now",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				diff := result.Sub(now)
				if diff < -time.Second || diff > time.Second {
					t.Errorf("expected 'now' to be within 1 second of current time, got diff: %v", diff)
				}
			},
		},
		{
			name:  "NOW (case insensitive)",
			input: "NOW",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				diff := result.Sub(now)
				if diff < -time.Second || diff > time.Second {
					t.Errorf("expected 'NOW' to be within 1 second of current time, got diff: %v", diff)
				}
			},
		},
		{
			name:  "today",
			input: "today",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
		{
			name:  "yesterday",
			input: "yesterday",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := now.AddDate(0, 0, -1)
				diff := result.Sub(expected)
				if diff < -time.Second || diff > time.Second {
					t.Errorf("expected 'yesterday' to be within 1 second of expected, got diff: %v", diff)
				}
			},
		},
		{
			name:  "tomorrow",
			input: "tomorrow",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := now.AddDate(0, 0, 1)
				diff := result.Sub(expected)
				if diff < -time.Second || diff > time.Second {
					t.Errorf("expected 'tomorrow' to be within 1 second of expected, got diff: %v", diff)
				}
			},
		},
		{
			name:  "everbefore",
			input: "everbefore",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(1970, 1, 1, 0, 0, 0, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
		{
			name:  "beginning",
			input: "beginning",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(1970, 1, 1, 0, 0, 0, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
		{
			name:  "end",
			input: "end",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
		{
			name:  "forever",
			input: "forever",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
		{
			name:  "always",
			input: "always",
			validate: func(t *testing.T, result time.Time, now time.Time) {
				expected := time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location())
				if !result.Equal(expected) {
					t.Errorf("expected %v, got %v", expected, result)
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			tt.validate(t, result, now)
		})
	}
}

func TestParseHumanizedTime_RelativeAgo(t *testing.T) {
	tests := []struct {
		name           string
		input          string
		expectedOffset func(now time.Time) time.Time
	}{
		{
			name:  "1 second ago",
			input: "1 second ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Second)
			},
		},
		{
			name:  "5 seconds ago",
			input: "5 seconds ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-5 * time.Second)
			},
		},
		{
			name:  "1 minute ago",
			input: "1 minute ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Minute)
			},
		},
		{
			name:  "30 minutes ago",
			input: "30 minutes ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-30 * time.Minute)
			},
		},
		{
			name:  "1 hour ago",
			input: "1 hour ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Hour)
			},
		},
		{
			name:  "24 hours ago",
			input: "24 hours ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-24 * time.Hour)
			},
		},
		{
			name:  "1 day ago",
			input: "1 day ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -1)
			},
		},
		{
			name:  "7 days ago",
			input: "7 days ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -7)
			},
		},
		{
			name:  "1 week ago",
			input: "1 week ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -7)
			},
		},
		{
			name:  "2 weeks ago",
			input: "2 weeks ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -14)
			},
		},
		{
			name:  "1 month ago",
			input: "1 month ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, -1, 0)
			},
		},
		{
			name:  "6 months ago",
			input: "6 months ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, -6, 0)
			},
		},
		{
			name:  "1 year ago",
			input: "1 year ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(-1, 0, 0)
			},
		},
		{
			name:  "5 years ago",
			input: "5 years ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(-5, 0, 0)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			expected := tt.expectedOffset(now)
			diff := result.Sub(expected)
			if diff < -time.Second || diff > time.Second {
				t.Errorf("expected %v, got %v (diff: %v)", expected, result, diff)
			}
		})
	}
}

func TestParseHumanizedTime_RelativeFuture(t *testing.T) {
	tests := []struct {
		name           string
		input          string
		expectedOffset func(now time.Time) time.Time
	}{
		{
			name:  "in 1 second",
			input: "in 1 second",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(1 * time.Second)
			},
		},
		{
			name:  "in 5 minutes",
			input: "in 5 minutes",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(5 * time.Minute)
			},
		},
		{
			name:  "in 2 hours",
			input: "in 2 hours",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(2 * time.Hour)
			},
		},
		{
			name:  "in 3 days",
			input: "in 3 days",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, 3)
			},
		},
		{
			name:  "in 1 week",
			input: "in 1 week",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, 7)
			},
		},
		{
			name:  "in 2 months",
			input: "in 2 months",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 2, 0)
			},
		},
		{
			name:  "in 1 year",
			input: "in 1 year",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(1, 0, 0)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			expected := tt.expectedOffset(now)
			diff := result.Sub(expected)
			if diff < -time.Second || diff > time.Second {
				t.Errorf("expected %v, got %v (diff: %v)", expected, result, diff)
			}
		})
	}
}

func TestParseHumanizedTime_LastNext(t *testing.T) {
	tests := []struct {
		name           string
		input          string
		expectedOffset func(now time.Time) time.Time
	}{
		{
			name:  "last week",
			input: "last week",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -7)
			},
		},
		{
			name:  "last month",
			input: "last month",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, -1, 0)
			},
		},
		{
			name:  "last year",
			input: "last year",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(-1, 0, 0)
			},
		},
		{
			name:  "next week",
			input: "next week",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, 7)
			},
		},
		{
			name:  "next month",
			input: "next month",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 1, 0)
			},
		},
		{
			name:  "next year",
			input: "next year",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(1, 0, 0)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			expected := tt.expectedOffset(now)
			diff := result.Sub(expected)
			if diff < -time.Second || diff > time.Second {
				t.Errorf("expected %v, got %v (diff: %v)", expected, result, diff)
			}
		})
	}
}

func TestParseHumanizedTime_ArticleForm(t *testing.T) {
	tests := []struct {
		name           string
		input          string
		expectedOffset func(now time.Time) time.Time
	}{
		{
			name:  "a second ago",
			input: "a second ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Second)
			},
		},
		{
			name:  "a minute ago",
			input: "a minute ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Minute)
			},
		},
		{
			name:  "an hour ago",
			input: "an hour ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.Add(-1 * time.Hour)
			},
		},
		{
			name:  "a day ago",
			input: "a day ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -1)
			},
		},
		{
			name:  "a week ago",
			input: "a week ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, 0, -7)
			},
		},
		{
			name:  "a month ago",
			input: "a month ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(0, -1, 0)
			},
		},
		{
			name:  "a year ago",
			input: "a year ago",
			expectedOffset: func(now time.Time) time.Time {
				return now.AddDate(-1, 0, 0)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			expected := tt.expectedOffset(now)
			diff := result.Sub(expected)
			if diff < -time.Second || diff > time.Second {
				t.Errorf("expected %v, got %v (diff: %v)", expected, result, diff)
			}
		})
	}
}

func TestParseHumanizedTime_StandardFormats(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected time.Time
	}{
		{
			name:     "DateOnly format",
			input:    "2024-03-15",
			expected: time.Date(2024, 3, 15, 0, 0, 0, 0, time.UTC),
		},
		{
			name:     "TimeOnly format",
			input:    "14:30:00",
			expected: time.Date(0, 1, 1, 14, 30, 0, 0, time.UTC),
		},
		{
			name:     "DateTime format",
			input:    "2024-03-15 14:30:00",
			expected: time.Date(2024, 3, 15, 14, 30, 0, 0, time.UTC),
		},
		// NOTE: RFC3339 format does NOT work because ParseHumanizedTime lowercases input,
		// which breaks the required "T" separator in RFC3339. This is a known limitation.
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			if !result.Equal(tt.expected) {
				t.Errorf("expected %v, got %v", tt.expected, result)
			}
		})
	}
}

func TestParseHumanizedTime_WhitespaceHandling(t *testing.T) {
	tests := []struct {
		name  string
		input string
	}{
		{name: "leading space", input: "  now"},
		{name: "trailing space", input: "now  "},
		{name: "both spaces", input: "  now  "},
		{name: "tabs", input: "\tnow\t"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			now := time.Now()
			result, err := ParseHumanizedTime(tt.input)
			if err != nil {
				t.Fatalf("ParseHumanizedTime(%q) returned error: %v", tt.input, err)
			}
			diff := result.Sub(now)
			if diff < -time.Second || diff > time.Second {
				t.Errorf("expected result to be within 1 second of now, got diff: %v", diff)
			}
		})
	}
}

func TestParseHumanizedTime_Errors(t *testing.T) {
	invalidInputs := []string{
		"invalid",
		"some random text",
		"not a time",
		"10 fortnights ago",
		"next fortnight",
		"in 5 blorbos",
		"",
	}

	for _, input := range invalidInputs {
		t.Run("invalid: "+input, func(t *testing.T) {
			_, err := ParseHumanizedTime(input)
			if err == nil {
				t.Errorf("expected error for input %q, got nil", input)
			}
		})
	}
}

func TestSubtractTime(t *testing.T) {
	baseTime := time.Date(2024, 6, 15, 12, 30, 45, 0, time.UTC)

	tests := []struct {
		name     string
		amount   int
		unit     string
		expected time.Time
	}{
		{"1 second", 1, "second", baseTime.Add(-1 * time.Second)},
		{"30 seconds", 30, "second", baseTime.Add(-30 * time.Second)},
		{"1 minute", 1, "minute", baseTime.Add(-1 * time.Minute)},
		{"45 minutes", 45, "minute", baseTime.Add(-45 * time.Minute)},
		{"1 hour", 1, "hour", baseTime.Add(-1 * time.Hour)},
		{"24 hours", 24, "hour", baseTime.Add(-24 * time.Hour)},
		{"1 day", 1, "day", baseTime.AddDate(0, 0, -1)},
		{"7 days", 7, "day", baseTime.AddDate(0, 0, -7)},
		{"1 week", 1, "week", baseTime.AddDate(0, 0, -7)},
		{"4 weeks", 4, "week", baseTime.AddDate(0, 0, -28)},
		{"1 month", 1, "month", baseTime.AddDate(0, -1, 0)},
		{"12 months", 12, "month", baseTime.AddDate(0, -12, 0)},
		{"1 year", 1, "year", baseTime.AddDate(-1, 0, 0)},
		{"5 years", 5, "year", baseTime.AddDate(-5, 0, 0)},
		{"unknown unit returns same time", 5, "unknown", baseTime},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := subtractTime(baseTime, tt.amount, tt.unit)
			if !result.Equal(tt.expected) {
				t.Errorf("subtractTime(%v, %d, %q) = %v, want %v",
					baseTime, tt.amount, tt.unit, result, tt.expected)
			}
		})
	}
}

func TestAddTime(t *testing.T) {
	baseTime := time.Date(2024, 6, 15, 12, 30, 45, 0, time.UTC)

	tests := []struct {
		name     string
		amount   int
		unit     string
		expected time.Time
	}{
		{"1 second", 1, "second", baseTime.Add(1 * time.Second)},
		{"30 seconds", 30, "second", baseTime.Add(30 * time.Second)},
		{"1 minute", 1, "minute", baseTime.Add(1 * time.Minute)},
		{"45 minutes", 45, "minute", baseTime.Add(45 * time.Minute)},
		{"1 hour", 1, "hour", baseTime.Add(1 * time.Hour)},
		{"24 hours", 24, "hour", baseTime.Add(24 * time.Hour)},
		{"1 day", 1, "day", baseTime.AddDate(0, 0, 1)},
		{"7 days", 7, "day", baseTime.AddDate(0, 0, 7)},
		{"1 week", 1, "week", baseTime.AddDate(0, 0, 7)},
		{"4 weeks", 4, "week", baseTime.AddDate(0, 0, 28)},
		{"1 month", 1, "month", baseTime.AddDate(0, 1, 0)},
		{"12 months", 12, "month", baseTime.AddDate(0, 12, 0)},
		{"1 year", 1, "year", baseTime.AddDate(1, 0, 0)},
		{"5 years", 5, "year", baseTime.AddDate(5, 0, 0)},
		{"unknown unit returns same time", 5, "unknown", baseTime},
		{"negative amount", -3, "day", baseTime.AddDate(0, 0, -3)},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := addTime(baseTime, tt.amount, tt.unit)
			if !result.Equal(tt.expected) {
				t.Errorf("addTime(%v, %d, %q) = %v, want %v",
					baseTime, tt.amount, tt.unit, result, tt.expected)
			}
		})
	}
}

func BenchmarkParseHumanizedTime(b *testing.B) {
	inputs := []string{
		"now",
		"today",
		"5 days ago",
		"in 2 weeks",
		"last month",
		"a year ago",
		"2024-03-15",
		"2024-03-15T14:30:00Z",
	}

	for _, input := range inputs {
		b.Run(input, func(b *testing.B) {
			for i := 0; i < b.N; i++ {
				ParseHumanizedTime(input)
			}
		})
	}
}
