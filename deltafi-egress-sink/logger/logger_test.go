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

// ABOUTME: Tests for the structured JSON logger package.
// ABOUTME: Verifies log formatting, level handling, and JSON output structure.

package logger

import (
	"bytes"
	"encoding/json"
	"log"
	"os"
	"strings"
	"testing"
	"time"
)

func TestCustomLogMarshal(t *testing.T) {
	testTime := time.Date(2024, 1, 15, 10, 30, 0, 0, time.UTC)

	logEntry := CustomLog{
		Timestamp:  testTime,
		Level:      "INFO",
		LoggerName: "test-logger",
		HostName:   "test-host",
		Message:    "Test message",
	}

	jsonBytes, err := json.Marshal(logEntry)
	if err != nil {
		t.Fatalf("Failed to marshal CustomLog: %v", err)
	}

	jsonStr := string(jsonBytes)

	// Verify all fields are present
	expectedFields := []string{
		`"timestamp"`,
		`"level":"INFO"`,
		`"loggerName":"test-logger"`,
		`"hostName":"test-host"`,
		`"message":"Test message"`,
	}

	for _, field := range expectedFields {
		if !strings.Contains(jsonStr, field) {
			t.Errorf("JSON output missing expected field: %s\nGot: %s", field, jsonStr)
		}
	}
}

func TestCustomLogUnmarshal(t *testing.T) {
	jsonStr := `{"timestamp":"2024-01-15T10:30:00Z","level":"ERROR","loggerName":"my-logger","hostName":"my-host","message":"Error occurred"}`

	var logEntry CustomLog
	err := json.Unmarshal([]byte(jsonStr), &logEntry)
	if err != nil {
		t.Fatalf("Failed to unmarshal CustomLog: %v", err)
	}

	if logEntry.Level != "ERROR" {
		t.Errorf("Level = %s, want ERROR", logEntry.Level)
	}
	if logEntry.LoggerName != "my-logger" {
		t.Errorf("LoggerName = %s, want my-logger", logEntry.LoggerName)
	}
	if logEntry.HostName != "my-host" {
		t.Errorf("HostName = %s, want my-host", logEntry.HostName)
	}
	if logEntry.Message != "Error occurred" {
		t.Errorf("Message = %s, want 'Error occurred'", logEntry.Message)
	}
}

// captureLogOutput captures log output during test execution
func captureLogOutput(f func()) string {
	var buf bytes.Buffer
	originalOutput := log.Writer()
	log.SetOutput(&buf)
	defer log.SetOutput(originalOutput)

	f()

	return buf.String()
}

func TestLogFunction(t *testing.T) {
	output := captureLogOutput(func() {
		Log("TEST", "test message")
	})

	// Parse the JSON output
	var logEntry CustomLog
	// Trim the newline added by log.Println
	output = strings.TrimSpace(output)
	err := json.Unmarshal([]byte(output), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output as JSON: %v\nOutput: %s", err, output)
	}

	if logEntry.Level != "TEST" {
		t.Errorf("Level = %s, want TEST", logEntry.Level)
	}
	if logEntry.Message != "test message" {
		t.Errorf("Message = %s, want 'test message'", logEntry.Message)
	}
	if logEntry.LoggerName != "deltafi-egress-sink" {
		t.Errorf("LoggerName = %s, want 'deltafi-egress-sink'", logEntry.LoggerName)
	}
}

func TestLogWithFormatting(t *testing.T) {
	tests := []struct {
		name        string
		msg         string
		args        []any
		wantMessage string
	}{
		{
			name:        "no formatting",
			msg:         "simple message",
			args:        nil,
			wantMessage: "simple message",
		},
		{
			name:        "string formatting",
			msg:         "hello %s",
			args:        []any{"world"},
			wantMessage: "hello world",
		},
		{
			name:        "integer formatting",
			msg:         "count: %d",
			args:        []any{42},
			wantMessage: "count: 42",
		},
		{
			name:        "multiple arguments",
			msg:         "%s has %d items",
			args:        []any{"list", 5},
			wantMessage: "list has 5 items",
		},
		{
			name:        "float formatting",
			msg:         "value: %.2f",
			args:        []any{3.14159},
			wantMessage: "value: 3.14",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			output := captureLogOutput(func() {
				Log("INFO", tt.msg, tt.args...)
			})

			var logEntry CustomLog
			err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
			if err != nil {
				t.Fatalf("Failed to parse log output: %v", err)
			}

			if logEntry.Message != tt.wantMessage {
				t.Errorf("Message = %q, want %q", logEntry.Message, tt.wantMessage)
			}
		})
	}
}

func TestInfoLevel(t *testing.T) {
	output := captureLogOutput(func() {
		Info("info message")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Level != "INFO" {
		t.Errorf("Level = %s, want INFO", logEntry.Level)
	}
	if logEntry.Message != "info message" {
		t.Errorf("Message = %s, want 'info message'", logEntry.Message)
	}
}

func TestDebugLevel(t *testing.T) {
	output := captureLogOutput(func() {
		Debug("debug message")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Level != "DEBUG" {
		t.Errorf("Level = %s, want DEBUG", logEntry.Level)
	}
}

func TestWarnLevel(t *testing.T) {
	output := captureLogOutput(func() {
		Warn("warning message")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Level != "WARN" {
		t.Errorf("Level = %s, want WARN", logEntry.Level)
	}
}

func TestErrorLevel(t *testing.T) {
	output := captureLogOutput(func() {
		Error("error message")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Level != "ERROR" {
		t.Errorf("Level = %s, want ERROR", logEntry.Level)
	}
}

func TestTimestampIsRecent(t *testing.T) {
	before := time.Now()

	output := captureLogOutput(func() {
		Info("timestamp test")
	})

	after := time.Now()

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Timestamp.Before(before) {
		t.Errorf("Timestamp %v is before test start %v", logEntry.Timestamp, before)
	}
	if logEntry.Timestamp.After(after) {
		t.Errorf("Timestamp %v is after test end %v", logEntry.Timestamp, after)
	}
}

func TestHostnameIsSet(t *testing.T) {
	output := captureLogOutput(func() {
		Info("hostname test")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.HostName == "" {
		t.Error("HostName should not be empty")
	}

	// Verify it matches the actual hostname
	expectedHostname, err := os.Hostname()
	if err != nil {
		t.Fatalf("Failed to get hostname: %v", err)
	}

	if logEntry.HostName != expectedHostname {
		t.Errorf("HostName = %s, want %s", logEntry.HostName, expectedHostname)
	}
}

func TestLoggerNameIsConstant(t *testing.T) {
	levels := []func(string, ...any){Info, Debug, Warn, Error}
	levelNames := []string{"Info", "Debug", "Warn", "Error"}

	for i, logFunc := range levels {
		t.Run(levelNames[i], func(t *testing.T) {
			output := captureLogOutput(func() {
				logFunc("test")
			})

			var logEntry CustomLog
			err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
			if err != nil {
				t.Fatalf("Failed to parse log output: %v", err)
			}

			if logEntry.LoggerName != "deltafi-egress-sink" {
				t.Errorf("LoggerName = %s, want 'deltafi-egress-sink'", logEntry.LoggerName)
			}
		})
	}
}

func TestSpecialCharactersInMessage(t *testing.T) {
	tests := []struct {
		name    string
		message string
	}{
		{"quotes", `message with "quotes"`},
		{"newlines", "message with\nnewlines"},
		{"tabs", "message with\ttabs"},
		{"unicode", "message with 世界 unicode"},
		{"backslashes", `message with \ backslashes`},
		{"html", "message with <html> tags"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			output := captureLogOutput(func() {
				Info("%s", tt.message)
			})

			var logEntry CustomLog
			err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
			if err != nil {
				t.Fatalf("Failed to parse log output: %v\nOutput: %s", err, output)
			}

			if logEntry.Message != tt.message {
				t.Errorf("Message = %q, want %q", logEntry.Message, tt.message)
			}
		})
	}
}

func TestEmptyMessage(t *testing.T) {
	output := captureLogOutput(func() {
		Info("")
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Message != "" {
		t.Errorf("Message = %q, want empty string", logEntry.Message)
	}
}

func TestVeryLongMessage(t *testing.T) {
	longMessage := strings.Repeat("a", 10000)

	output := captureLogOutput(func() {
		Info("%s", longMessage)
	})

	var logEntry CustomLog
	err := json.Unmarshal([]byte(strings.TrimSpace(output)), &logEntry)
	if err != nil {
		t.Fatalf("Failed to parse log output: %v", err)
	}

	if logEntry.Message != longMessage {
		t.Errorf("Long message was not preserved correctly, got length %d, want %d",
			len(logEntry.Message), len(longMessage))
	}
}

// BenchmarkLogInfo benchmarks the Info logging function
func BenchmarkLogInfo(b *testing.B) {
	// Discard output during benchmark
	log.SetOutput(&bytes.Buffer{})
	defer log.SetOutput(os.Stdout)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		Info("benchmark message %d", i)
	}
}

// BenchmarkLogWithFormatting benchmarks formatted logging
func BenchmarkLogWithFormatting(b *testing.B) {
	log.SetOutput(&bytes.Buffer{})
	defer log.SetOutput(os.Stdout)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		Info("user %s performed action %s with result %d", "user123", "login", 200)
	}
}
