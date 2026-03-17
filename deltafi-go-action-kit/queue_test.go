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
// ABOUTME: Tests for queue utility functions (no Redis required).
// ABOUTME: Verifies parseHostPort and resultQueueName logic.

package actionkit

import "testing"

func TestParseHostPort(t *testing.T) {
	tests := []struct {
		input    string
		wantHost string
		wantPort string
	}{
		{"localhost:6379", "localhost", "6379"},
		{"redis.example.com:6380", "redis.example.com", "6380"},
		{"my-host", "my-host", "6379"}, // no port defaults to 6379
		{"192.168.1.1:7000", "192.168.1.1", "7000"},
		{"host:with:colons:6379", "host:with:colons", "6379"},           // splits on last colon
		{"http://deltafi-valkey:6379", "deltafi-valkey", "6379"},        // strips http scheme
		{"redis://redis.example.com:6380", "redis.example.com", "6380"}, // strips redis scheme
		{"http://my-host", "my-host", "6379"},                           // scheme + no port
	}

	for _, tt := range tests {
		host, port := parseHostPort(tt.input)
		if host != tt.wantHost || port != tt.wantPort {
			t.Errorf("parseHostPort(%q) = (%q, %q), want (%q, %q)",
				tt.input, host, port, tt.wantHost, tt.wantPort)
		}
	}
}

func TestResultQueueName(t *testing.T) {
	if got := resultQueueName(""); got != "dgs" {
		t.Errorf("resultQueueName(%q) = %q, want %q", "", got, "dgs")
	}
	if got := resultQueueName("core-1"); got != "dgs-core-1" {
		t.Errorf("resultQueueName(%q) = %q, want %q", "core-1", got, "dgs-core-1")
	}
	if got := resultQueueName("abc"); got != "dgs-abc" {
		t.Errorf("resultQueueName(%q) = %q, want %q", "abc", got, "dgs-abc")
	}
}
