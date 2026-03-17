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
// ABOUTME: Tests for logging types.
// ABOUTME: Verifies LogMessage creation with correct severity, source, and timestamp format.

package actionkit

import (
	"testing"
	"time"
)

func TestNewLogMessage(t *testing.T) {
	before := time.Now()
	msg := newLogMessage(SeverityInfo, "TestAction", "hello world")
	after := time.Now()

	if msg.Severity != SeverityInfo {
		t.Errorf("Severity = %q, want %q", msg.Severity, SeverityInfo)
	}
	if msg.Source != "TestAction" {
		t.Errorf("Source = %q, want %q", msg.Source, "TestAction")
	}
	if msg.Message != "hello world" {
		t.Errorf("Message = %q, want %q", msg.Message, "hello world")
	}

	// Verify Created is a valid RFC3339Nano timestamp in the right range
	created, err := time.Parse(time.RFC3339Nano, msg.Created)
	if err != nil {
		t.Fatalf("Created is not valid RFC3339Nano: %q, err: %v", msg.Created, err)
	}
	if created.Before(before) || created.After(after) {
		t.Errorf("Created %v not in range [%v, %v]", created, before, after)
	}
}

func TestNewLogMessage_AllSeverities(t *testing.T) {
	tests := []struct {
		severity LogSeverity
		want     string
	}{
		{SeverityInfo, "INFO"},
		{SeverityWarning, "WARNING"},
		{SeverityError, "ERROR"},
	}

	for _, tt := range tests {
		msg := newLogMessage(tt.severity, "src", "msg")
		if string(msg.Severity) != tt.want {
			t.Errorf("severity %v: got %q, want %q", tt.severity, msg.Severity, tt.want)
		}
	}
}
