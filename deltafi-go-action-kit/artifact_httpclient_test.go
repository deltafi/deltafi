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
// ABOUTME: Tests for the artifact HTTP client's timeout configuration.
// ABOUTME: Verifies header-timeout protection without bounding body streaming time.

package actionkit

import (
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestNewArtifactHTTPClient_NoWholeResponseTimeout(t *testing.T) {
	// A whole-response Timeout would abort an in-progress streaming body read
	// of a large artifact. The client must rely on context for cancellation
	// instead, so Timeout must be zero.
	if to := newArtifactHTTPClient().Timeout; to != 0 {
		t.Fatalf("artifact client Timeout = %v, want 0 (no whole-response deadline)", to)
	}
}

func TestNewArtifactHTTPClient_StreamsBodySlowerThanHeaderTimeout(t *testing.T) {
	old := artifactResponseHeaderTimeout
	artifactResponseHeaderTimeout = 50 * time.Millisecond
	defer func() { artifactResponseHeaderTimeout = old }()

	body := "the-artifact-payload"
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.(http.Flusher).Flush() // headers arrive immediately
		// Body trickles in well after the header timeout would have fired.
		time.Sleep(4 * artifactResponseHeaderTimeout)
		_, _ = w.Write([]byte(body))
	}))
	defer srv.Close()

	resp, err := newArtifactHTTPClient().Get(srv.URL)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading slow body failed (timeout aborted the stream?): %v", err)
	}
	if string(data) != body {
		t.Fatalf("body = %q, want %q", data, body)
	}
}

func TestNewArtifactHTTPClient_HeaderTimeoutAbortsHungServer(t *testing.T) {
	old := artifactResponseHeaderTimeout
	artifactResponseHeaderTimeout = 50 * time.Millisecond
	defer func() { artifactResponseHeaderTimeout = old }()

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Never sends headers within the timeout window.
		time.Sleep(4 * artifactResponseHeaderTimeout)
		_, _ = w.Write([]byte("too late"))
	}))
	defer srv.Close()

	_, err := newArtifactHTTPClient().Get(srv.URL)
	if err == nil {
		t.Fatal("expected an error when response headers exceed the header timeout")
	}
}
