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
// ABOUTME: Tests for the artifact registry client.
// ABOUTME: Verifies publish, download, streaming, and manifest parsing.

package actionkit

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

func TestArtifactClient_PublishArtifact(t *testing.T) {
	var capturedPath string
	var capturedContentType string
	var capturedHeaders http.Header

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		capturedContentType = r.Header.Get("Content-Type")
		capturedHeaders = r.Header

		// Verify multipart
		if !strings.HasPrefix(capturedContentType, "multipart/form-data") {
			t.Errorf("expected multipart content type, got %q", capturedContentType)
		}

		// Parse multipart to verify file content
		if err := r.ParseMultipartForm(10 << 20); err != nil {
			t.Fatalf("failed to parse multipart: %v", err)
		}
		file, _, err := r.FormFile("file")
		if err != nil {
			t.Fatalf("missing file field: %v", err)
		}
		defer file.Close()
		data, _ := io.ReadAll(file)
		if string(data) != "model-bytes" {
			t.Errorf("file data = %q, want %q", string(data), "model-bytes")
		}

		if r.FormValue("version") != "1.0.0" {
			t.Errorf("version = %q, want %q", r.FormValue("version"), "1.0.0")
		}
		if r.FormValue("description") != "test model" {
			t.Errorf("description = %q, want %q", r.FormValue("description"), "test model")
		}

		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(PublishResponse{
			ArtifactName: "my-model",
			Version:      "1.0.0",
			IsActive:     true,
			SHA256:       "abc123",
		})
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "org.deltafi.test-plugin")
	resp, err := client.PublishArtifact(ctx(), "my-model", strings.NewReader("model-bytes"), "application/octet-stream", &PublishOptions{
		Version:     "1.0.0",
		Description: "test model",
	})
	if err != nil {
		t.Fatalf("PublishArtifact failed: %v", err)
	}

	if capturedPath != "/api/v2/artifacts/org.deltafi.test-plugin/my-model" {
		t.Errorf("path = %q, want plugin-scoped path", capturedPath)
	}
	if capturedHeaders.Get("X-User-Permissions") != "Admin" {
		t.Error("missing X-User-Permissions header")
	}
	if capturedHeaders.Get("X-User-Name") != "go-plugin" {
		t.Error("missing X-User-Name header")
	}
	if resp.ArtifactName != "my-model" {
		t.Errorf("ArtifactName = %q, want %q", resp.ArtifactName, "my-model")
	}
	if resp.Version != "1.0.0" {
		t.Errorf("Version = %q, want %q", resp.Version, "1.0.0")
	}
	if !resp.IsActive {
		t.Error("IsActive should be true")
	}
	if resp.SHA256 != "abc123" {
		t.Errorf("SHA256 = %q, want %q", resp.SHA256, "abc123")
	}
}

func TestArtifactClient_PublishSharedArtifact(t *testing.T) {
	var capturedPath string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(PublishResponse{
			ArtifactName: "shared-model",
			Version:      "v1",
			IsActive:     true,
			SHA256:       "def456",
		})
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "org.deltafi.test-plugin")
	_, err := client.PublishSharedArtifact(ctx(), "shared-model", strings.NewReader("data"), "application/octet-stream", nil)
	if err != nil {
		t.Fatalf("PublishSharedArtifact failed: %v", err)
	}

	if capturedPath != "/api/v2/artifacts/shared/shared-model" {
		t.Errorf("path = %q, want shared path", capturedPath)
	}
}

func TestArtifactClient_PublishArtifact_WithAllOptions(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.ParseMultipartForm(10 << 20)

		if r.FormValue("makeActive") != "false" {
			t.Errorf("makeActive = %q, want %q", r.FormValue("makeActive"), "false")
		}
		if r.FormValue("alias") != "staging" {
			t.Errorf("alias = %q, want %q", r.FormValue("alias"), "staging")
		}
		if r.FormValue("env") != "prod" {
			t.Errorf("label env = %q, want %q", r.FormValue("env"), "prod")
		}

		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(PublishResponse{ArtifactName: "m", Version: "v1"})
	}))
	defer server.Close()

	makeActive := false
	client := newArtifactClient(server.URL, "owner")
	_, err := client.PublishArtifact(ctx(), "m", strings.NewReader("data"), "text/plain", &PublishOptions{
		MakeActive: &makeActive,
		Alias:      "staging",
		Labels:     map[string]string{"env": "prod"},
	})
	if err != nil {
		t.Fatalf("PublishArtifact failed: %v", err)
	}
}

func TestArtifactClient_PublishArtifact_Error(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		w.Write([]byte("version already exists"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	_, err := client.PublishArtifact(ctx(), "m", strings.NewReader("data"), "text/plain", nil)
	if err == nil {
		t.Fatal("expected error for HTTP 409")
	}
	if !strings.Contains(err.Error(), "409") {
		t.Errorf("error should mention status code: %v", err)
	}
}

func TestArtifactClient_GetArtifact(t *testing.T) {
	var capturedPath string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.Header().Set("X-Artifact-Version", "20260314.120000.000")
		w.Header().Set("X-Artifact-Sha256", "abc123")
		w.Header().Set("X-Artifact-Description", "test artifact")
		w.Header().Set("X-Artifact-Source-Prefix", "plugins/org.deltafi.test")
		w.Header().Set("X-Artifact-Labels", `{"env":"prod","tier":"1"}`)
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Write([]byte("artifact-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "org.deltafi.test")
	fetched, err := client.GetArtifact(ctx(), "my-model", "")
	if err != nil {
		t.Fatalf("GetArtifact failed: %v", err)
	}

	if capturedPath != "/api/v2/artifacts/org.deltafi.test/my-model/versions/active" {
		t.Errorf("path = %q, want active alias path", capturedPath)
	}
	if string(fetched.Data) != "artifact-data" {
		t.Errorf("Data = %q, want %q", string(fetched.Data), "artifact-data")
	}
	if fetched.Manifest.Version != "20260314.120000.000" {
		t.Errorf("Version = %q", fetched.Manifest.Version)
	}
	if fetched.Manifest.SHA256 != "abc123" {
		t.Errorf("SHA256 = %q", fetched.Manifest.SHA256)
	}
	if fetched.Manifest.Description != "test artifact" {
		t.Errorf("Description = %q", fetched.Manifest.Description)
	}
	if fetched.Manifest.ContentType != "application/octet-stream" {
		t.Errorf("ContentType = %q", fetched.Manifest.ContentType)
	}
	if fetched.Manifest.SourcePrefix != "plugins/org.deltafi.test" {
		t.Errorf("SourcePrefix = %q", fetched.Manifest.SourcePrefix)
	}
	if fetched.Manifest.Labels["env"] != "prod" {
		t.Errorf("Labels[env] = %q, want %q", fetched.Manifest.Labels["env"], "prod")
	}
	if fetched.Manifest.Labels["tier"] != "1" {
		t.Errorf("Labels[tier] = %q, want %q", fetched.Manifest.Labels["tier"], "1")
	}
}

func TestArtifactClient_GetArtifact_WithAlias(t *testing.T) {
	var capturedPath string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.Write([]byte("v2-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	fetched, err := client.GetArtifact(ctx(), "model", "v2")
	if err != nil {
		t.Fatalf("GetArtifact failed: %v", err)
	}

	if capturedPath != "/api/v2/artifacts/owner/model/versions/v2" {
		t.Errorf("path = %q, want specific version path", capturedPath)
	}
	if string(fetched.Data) != "v2-data" {
		t.Errorf("Data = %q", string(fetched.Data))
	}
}

func TestArtifactClient_GetSharedArtifact(t *testing.T) {
	var capturedPath string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.Write([]byte("shared-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	fetched, err := client.GetSharedArtifact(ctx(), "shared-model", "")
	if err != nil {
		t.Fatalf("GetSharedArtifact failed: %v", err)
	}

	if capturedPath != "/api/v2/artifacts/shared/shared-model/versions/active" {
		t.Errorf("path = %q, want shared path", capturedPath)
	}
	if string(fetched.Data) != "shared-data" {
		t.Errorf("Data = %q", string(fetched.Data))
	}
}

func TestArtifactClient_GetArtifact_NotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	_, err := client.GetArtifact(ctx(), "missing", "")
	if err == nil {
		t.Fatal("expected error for missing artifact")
	}
	if !strings.Contains(err.Error(), "not found") {
		t.Errorf("error should mention 'not found': %v", err)
	}
}

func TestArtifactClient_GetArtifact_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	_, err := client.GetArtifact(ctx(), "model", "")
	if err == nil {
		t.Fatal("expected error for HTTP 500")
	}
	if !strings.Contains(err.Error(), "500") {
		t.Errorf("error should mention status code: %v", err)
	}
}

func TestArtifactClient_StreamArtifact(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Artifact-Version", "v3")
		w.Header().Set("X-Artifact-Sha256", "sha-stream")
		w.Write([]byte("streamed-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	stream, err := client.StreamArtifact(ctx(), "model", "v3")
	if err != nil {
		t.Fatalf("StreamArtifact failed: %v", err)
	}
	defer stream.Reader.Close()

	data, err := io.ReadAll(stream.Reader)
	if err != nil {
		t.Fatalf("failed to read stream: %v", err)
	}
	if string(data) != "streamed-data" {
		t.Errorf("streamed data = %q", string(data))
	}
	if stream.Manifest.Version != "v3" {
		t.Errorf("Version = %q", stream.Manifest.Version)
	}
	if stream.Manifest.SHA256 != "sha-stream" {
		t.Errorf("SHA256 = %q", stream.Manifest.SHA256)
	}
}

func TestArtifactClient_StreamSharedArtifact(t *testing.T) {
	var capturedPath string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.Write([]byte("shared-stream"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	stream, err := client.StreamSharedArtifact(ctx(), "shared-model", "")
	if err != nil {
		t.Fatalf("StreamSharedArtifact failed: %v", err)
	}
	defer stream.Reader.Close()

	if capturedPath != "/api/v2/artifacts/shared/shared-model/versions/active" {
		t.Errorf("path = %q, want shared path", capturedPath)
	}
}

func TestParseManifestHeaders_NoLabels(t *testing.T) {
	resp := &http.Response{
		Header: http.Header{
			"X-Artifact-Version": {"v1"},
			"X-Artifact-Sha256":  {"abc"},
			"Content-Type":       {"text/plain"},
		},
	}
	m := parseManifestHeaders(resp)
	if m.Version != "v1" {
		t.Errorf("Version = %q", m.Version)
	}
	if m.Labels != nil {
		t.Errorf("Labels should be nil when header is absent")
	}
}

func TestParseManifestHeaders_InvalidLabelsJSON(t *testing.T) {
	resp := &http.Response{
		Header: http.Header{
			"X-Artifact-Labels": {"not-json"},
		},
	}
	m := parseManifestHeaders(resp)
	if m.Labels != nil {
		t.Error("Labels should be nil for invalid JSON")
	}
}

func TestArtifactClient_PublishArtifact_LargeData(t *testing.T) {
	var receivedSize int

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.ParseMultipartForm(10 << 20)
		file, _, _ := r.FormFile("file")
		data, _ := io.ReadAll(file)
		receivedSize = len(data)
		file.Close()

		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(PublishResponse{ArtifactName: "big", Version: "v1"})
	}))
	defer server.Close()

	// 1MB of data
	bigData := bytes.Repeat([]byte("x"), 1<<20)
	client := newArtifactClient(server.URL, "owner")
	_, err := client.PublishArtifact(ctx(), "big", bytes.NewReader(bigData), "application/octet-stream", nil)
	if err != nil {
		t.Fatalf("PublishArtifact failed: %v", err)
	}
	if receivedSize != 1<<20 {
		t.Errorf("received %d bytes, want %d", receivedSize, 1<<20)
	}
}

func TestArtifactClient_GetArtifact_Cached(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	// First call hits server
	_, err := client.GetArtifact(ctx(), "model", "")
	if err != nil {
		t.Fatalf("GetArtifact 1 failed: %v", err)
	}
	if callCount != 1 {
		t.Fatalf("expected 1 server call, got %d", callCount)
	}

	// Second call should be cached
	_, err = client.GetArtifact(ctx(), "model", "")
	if err != nil {
		t.Fatalf("GetArtifact 2 failed: %v", err)
	}
	if callCount != 1 {
		t.Errorf("expected 1 server call (cached), got %d", callCount)
	}

	// Different name should miss cache
	_, err = client.GetArtifact(ctx(), "other-model", "")
	if err != nil {
		t.Fatalf("GetArtifact 3 failed: %v", err)
	}
	if callCount != 2 {
		t.Errorf("expected 2 server calls (cache miss), got %d", callCount)
	}

	// Different alias should miss cache
	_, err = client.GetArtifact(ctx(), "model", "v2")
	if err != nil {
		t.Fatalf("GetArtifact 4 failed: %v", err)
	}
	if callCount != 3 {
		t.Errorf("expected 3 server calls (different alias), got %d", callCount)
	}
}

func TestArtifactClient_GetArtifact_CacheDisabled(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(0)

	client.GetArtifact(ctx(), "model", "")
	client.GetArtifact(ctx(), "model", "")

	if callCount != 2 {
		t.Errorf("with TTL=0, expected 2 server calls, got %d", callCount)
	}
}

func TestArtifactClient_ClearCache(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(1 * time.Minute)

	client.GetArtifact(ctx(), "model", "")
	if callCount != 1 {
		t.Fatalf("expected 1 call, got %d", callCount)
	}

	client.ClearCache()

	client.GetArtifact(ctx(), "model", "")
	if callCount != 2 {
		t.Errorf("after ClearCache, expected 2 calls, got %d", callCount)
	}
}

func TestArtifactClient_GetArtifact_FailureCached(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("error"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	// First call fails
	_, err := client.GetArtifact(ctx(), "model", "")
	if err == nil {
		t.Fatal("expected error")
	}
	if callCount != 1 {
		t.Fatalf("expected 1 server call, got %d", callCount)
	}

	// Second call should return cached error
	_, err = client.GetArtifact(ctx(), "model", "")
	if err == nil {
		t.Fatal("expected cached error")
	}
	if callCount != 1 {
		t.Errorf("expected 1 server call (failure cached), got %d", callCount)
	}

	// After 1s, failure cache expires
	time.Sleep(1100 * time.Millisecond)
	_, err = client.GetArtifact(ctx(), "model", "")
	if err == nil {
		t.Fatal("expected error on retry")
	}
	if callCount != 2 {
		t.Errorf("expected 2 server calls after failure TTL expiry, got %d", callCount)
	}
}

func TestArtifactClient_GetArtifact_FailureRecovery(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		if callCount == 1 {
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte("error"))
			return
		}
		w.Write([]byte("recovered-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	// First call fails
	_, err := client.GetArtifact(ctx(), "model", "")
	if err == nil {
		t.Fatal("expected error")
	}

	// After failure TTL expires, should recover
	time.Sleep(1100 * time.Millisecond)
	fetched, err := client.GetArtifact(ctx(), "model", "")
	if err != nil {
		t.Fatalf("expected recovery, got error: %v", err)
	}
	if string(fetched.Data) != "recovered-data" {
		t.Errorf("Data = %q, want %q", string(fetched.Data), "recovered-data")
	}
}

func TestArtifactClient_PublishInvalidatesCache(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			callCount++
			w.Write([]byte("data"))
			return
		}
		// POST (publish)
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(PublishResponse{ArtifactName: "model", Version: "v2"})
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	// Populate cache
	client.GetArtifact(ctx(), "model", "")
	if callCount != 1 {
		t.Fatalf("expected 1 call, got %d", callCount)
	}

	// Publish should invalidate
	client.PublishArtifact(ctx(), "model", strings.NewReader("new"), "text/plain", nil)

	// Next get should miss cache
	client.GetArtifact(ctx(), "model", "")
	if callCount != 2 {
		t.Errorf("after publish, expected 2 calls, got %d", callCount)
	}
}

func TestArtifactClient_StreamBypasses_Cache(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Write([]byte("stream-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	s1, _ := client.StreamArtifact(ctx(), "model", "")
	s1.Reader.Close()
	s2, _ := client.StreamArtifact(ctx(), "model", "")
	s2.Reader.Close()

	if callCount != 2 {
		t.Errorf("stream should bypass cache, expected 2 calls, got %d", callCount)
	}
}

func TestArtifactClient_CacheTTL_FromEnv(t *testing.T) {
	os.Setenv("ARTIFACT_CACHE_TTL", "45")
	defer os.Unsetenv("ARTIFACT_CACHE_TTL")

	client := newArtifactClient("http://localhost", "owner")
	if got := time.Duration(client.cacheTTL.Load()); got != 45*time.Second {
		t.Errorf("cacheTTL = %v, want 45s from env", got)
	}
}

func TestArtifactClient_FailureCacheTTL_FromEnv(t *testing.T) {
	os.Setenv("ARTIFACT_FAILURE_CACHE_TTL", "3s")
	defer os.Unsetenv("ARTIFACT_FAILURE_CACHE_TTL")

	client := newArtifactClient("http://localhost", "owner")
	if got := time.Duration(client.failureCacheTTL.Load()); got != 3*time.Second {
		t.Errorf("failureCacheTTL = %v, want 3s from env", got)
	}
}

func TestArtifactClient_CacheTTL_Defaults(t *testing.T) {
	client := newArtifactClient("http://localhost", "owner")
	if got := time.Duration(client.cacheTTL.Load()); got != 10*time.Second {
		t.Errorf("cacheTTL = %v, want 10s default", got)
	}
	if got := time.Duration(client.failureCacheTTL.Load()); got != 1*time.Second {
		t.Errorf("failureCacheTTL = %v, want 1s default", got)
	}
}

func TestVersionChangedEvent_Owner(t *testing.T) {
	tests := []struct {
		name   string
		prefix string
		owner  string
		shared bool
	}{
		{"shared", "shared", "shared", true},
		{"plugin", "plugins/org.deltafi.test-plugin", "org.deltafi.test-plugin", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			event := VersionChangedEvent{Prefix: tt.prefix}
			if event.Owner() != tt.owner {
				t.Errorf("Owner() = %q, want %q", event.Owner(), tt.owner)
			}
			if event.IsShared() != tt.shared {
				t.Errorf("IsShared() = %v, want %v", event.IsShared(), tt.shared)
			}
		})
	}
}

func TestArtifactClient_OnUpdate_CallbackFired(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "org.deltafi.test")
	client.OnUpdate("my-model", "", func(event VersionChangedEvent) {
		callCount++
		if event.ArtifactName != "my-model" {
			t.Errorf("ArtifactName = %q", event.ArtifactName)
		}
		if event.NewVersion != "v2" {
			t.Errorf("NewVersion = %q", event.NewVersion)
		}
	})

	client.handleVersionChanged(`{"prefix":"plugins/org.deltafi.test","artifactName":"my-model","alias":"active","previousVersion":"v1","newVersion":"v2","timestamp":1773513189.850452385}`)

	if callCount != 1 {
		t.Errorf("expected callback to fire once, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdate_CacheInvalidatedBeforeCallback(t *testing.T) {
	fetchCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fetchCount++
		w.Write([]byte("data-v" + strings.TrimPrefix(r.URL.Path, "/api/v2/artifacts/owner/model/versions/")))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(1 * time.Minute) // long TTL so cache doesn't expire

	// Populate cache
	client.GetArtifact(ctx(), "model", "")
	if fetchCount != 1 {
		t.Fatalf("expected 1 fetch, got %d", fetchCount)
	}

	callbackFetched := false
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		// Cache should be invalidated, so this triggers a new fetch
		_, err := client.GetArtifact(ctx(), "model", "")
		if err != nil {
			t.Errorf("GetArtifact in callback failed: %v", err)
		}
		callbackFetched = true
	})

	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v2"}`)

	if !callbackFetched {
		t.Error("callback was not invoked")
	}
	if fetchCount != 2 {
		t.Errorf("expected 2 fetches (cache invalidated before callback), got %d", fetchCount)
	}
}

func TestArtifactClient_OnUpdate_SharedCallback(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "owner")
	client.OnSharedUpdate("shared-model", "", func(event VersionChangedEvent) {
		callCount++
	})

	client.handleVersionChanged(`{"prefix":"shared","artifactName":"shared-model","alias":"active","newVersion":"v1"}`)

	if callCount != 1 {
		t.Errorf("expected shared callback to fire, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdate_IgnoresOtherPlugins(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "my-plugin")
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		callCount++
	})

	// Event for a different plugin should be ignored
	client.handleVersionChanged(`{"prefix":"plugins/other-plugin","artifactName":"model","alias":"active","newVersion":"v1"}`)

	if callCount != 0 {
		t.Errorf("callback should not fire for other plugins, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdate_IgnoresNonMatchingArtifact(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "owner")
	client.OnUpdate("model-a", "", func(event VersionChangedEvent) {
		callCount++
	})

	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model-b","alias":"active","newVersion":"v1"}`)

	if callCount != 0 {
		t.Errorf("callback should not fire for non-matching artifact, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdate_IgnoresNonMatchingAlias(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "owner")
	client.OnUpdate("model", "staging", func(event VersionChangedEvent) {
		callCount++
	})

	// Event with alias "active" should not match "staging" callback
	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v1"}`)

	if callCount != 0 {
		t.Errorf("callback should not fire for non-matching alias, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdate_MultipleCallbacks(t *testing.T) {
	calls := []string{}
	client := newArtifactClient("http://localhost", "owner")
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		calls = append(calls, "cb1")
	})
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		calls = append(calls, "cb2")
	})

	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v1"}`)

	if len(calls) != 2 {
		t.Errorf("expected 2 callbacks, got %d", len(calls))
	}
}

func TestArtifactClient_OnUpdate_PanicRecovery(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "owner")
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		panic("deliberate panic")
	})
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		callCount++
	})

	// Should not panic; second callback should still fire
	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v1"}`)

	if callCount != 1 {
		t.Errorf("second callback should fire despite first panicking, got %d", callCount)
	}
}

func TestArtifactClient_FireStartupCallbacks(t *testing.T) {
	calls := []VersionChangedEvent{}
	client := newArtifactClient("http://localhost", "org.deltafi.test")
	client.OnUpdate("model-a", "", func(event VersionChangedEvent) {
		calls = append(calls, event)
	})
	client.OnSharedUpdate("shared-b", "v1", func(event VersionChangedEvent) {
		calls = append(calls, event)
	})

	client.FireStartupCallbacks()

	if len(calls) != 2 {
		t.Fatalf("expected 2 startup callbacks, got %d", len(calls))
	}

	// First callback: plugin-scoped
	if calls[0].Prefix != "plugins/org.deltafi.test" {
		t.Errorf("startup event 0 prefix = %q", calls[0].Prefix)
	}
	if calls[0].ArtifactName != "model-a" {
		t.Errorf("startup event 0 artifactName = %q", calls[0].ArtifactName)
	}
	if calls[0].Alias != "active" {
		t.Errorf("startup event 0 alias = %q", calls[0].Alias)
	}
	if calls[0].PreviousVersion != "" || calls[0].NewVersion != "" {
		t.Error("startup event should have empty versions")
	}

	// Second callback: shared
	if calls[1].Prefix != "shared" {
		t.Errorf("startup event 1 prefix = %q", calls[1].Prefix)
	}
	if calls[1].ArtifactName != "shared-b" {
		t.Errorf("startup event 1 artifactName = %q", calls[1].ArtifactName)
	}
	if calls[1].Alias != "v1" {
		t.Errorf("startup event 1 alias = %q", calls[1].Alias)
	}
}

func TestArtifactClient_FireStartupCallbacks_PanicRecovery(t *testing.T) {
	callCount := 0
	client := newArtifactClient("http://localhost", "owner")
	client.OnUpdate("model", "", func(event VersionChangedEvent) {
		panic("startup panic")
	})
	client.OnUpdate("other", "", func(event VersionChangedEvent) {
		callCount++
	})

	// Should not panic
	client.FireStartupCallbacks()

	if callCount != 1 {
		t.Errorf("second callback should fire despite first panicking, got %d", callCount)
	}
}

func TestArtifactClient_HandleVersionChanged_InvalidJSON(t *testing.T) {
	client := newArtifactClient("http://localhost", "owner")
	// Should not panic on invalid JSON
	client.handleVersionChanged("not-json")
}

func TestArtifactClient_HandleVersionChanged_InvalidatesCache(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(1 * time.Minute)

	// Populate cache
	client.GetArtifact(ctx(), "model", "")
	if callCount != 1 {
		t.Fatalf("expected 1 fetch, got %d", callCount)
	}

	// Version change should invalidate cache
	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v2"}`)

	// Next get should miss cache
	client.GetArtifact(ctx(), "model", "")
	if callCount != 2 {
		t.Errorf("expected 2 fetches after version change, got %d", callCount)
	}
}

func TestArtifactClient_OnUpdateFetch(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Artifact-Version", "v1")
		w.Write([]byte("fetched-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	var received *FetchedArtifact
	var receivedErr error

	client.OnUpdateFetch("model", "", func(fetched *FetchedArtifact, err error) {
		received = fetched
		receivedErr = err
	})

	client.FireStartupCallbacks()

	if receivedErr != nil {
		t.Fatalf("expected no error, got %v", receivedErr)
	}
	if string(received.Data) != "fetched-data" {
		t.Errorf("Data = %q, want %q", string(received.Data), "fetched-data")
	}
	if received.Manifest.Version != "v1" {
		t.Errorf("Version = %q", received.Manifest.Version)
	}
}

func TestArtifactClient_OnUpdateFetch_Error(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	var receivedErr error

	client.OnUpdateFetch("missing", "", func(fetched *FetchedArtifact, err error) {
		receivedErr = err
	})

	client.FireStartupCallbacks()

	if receivedErr == nil {
		t.Fatal("expected error for missing artifact")
	}
}

func TestArtifactClient_OnUpdateFetchWithDefault(t *testing.T) {
	fetchCount := 0
	publishCount := 0

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			publishCount++
			// Verify the published data
			r.ParseMultipartForm(10 << 20)
			file, _, _ := r.FormFile("file")
			data, _ := io.ReadAll(file)
			file.Close()
			if string(data) != `{"key":"default"}` {
				t.Errorf("published data = %q", string(data))
			}
			w.WriteHeader(http.StatusCreated)
			json.NewEncoder(w).Encode(PublishResponse{ArtifactName: "config.json", Version: "1"})
			return
		}
		fetchCount++
		if fetchCount == 1 {
			// First fetch: not found
			w.WriteHeader(http.StatusNotFound)
			return
		}
		// Second fetch: return the published data
		w.Write([]byte(`{"key":"default"}`))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(0) // disable caching so second get hits server
	var received *FetchedArtifact

	client.OnUpdateFetchWithDefault("config.json", "",
		ArtifactDefault{
			Data:        []byte(`{"key":"default"}`),
			ContentType: "application/json",
			Description: "test default",
		},
		func(fetched *FetchedArtifact, err error) {
			if err != nil {
				t.Fatalf("expected no error after default publish, got %v", err)
			}
			received = fetched
		})

	client.FireStartupCallbacks()

	if publishCount != 1 {
		t.Errorf("expected 1 publish, got %d", publishCount)
	}
	if fetchCount != 2 {
		t.Errorf("expected 2 fetches (miss + retry), got %d", fetchCount)
	}
	if string(received.Data) != `{"key":"default"}` {
		t.Errorf("Data = %q", string(received.Data))
	}
}

func TestArtifactClient_OnUpdateFetchWithDefault_AlreadyExists(t *testing.T) {
	publishCount := 0

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			publishCount++
			w.WriteHeader(http.StatusCreated)
			json.NewEncoder(w).Encode(PublishResponse{})
			return
		}
		w.Write([]byte("existing-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	var received *FetchedArtifact

	client.OnUpdateFetchWithDefault("config.json", "",
		ArtifactDefault{Data: []byte("default"), ContentType: "text/plain"},
		func(fetched *FetchedArtifact, err error) {
			received = fetched
		})

	client.FireStartupCallbacks()

	if publishCount != 0 {
		t.Errorf("should not publish when artifact exists, got %d publishes", publishCount)
	}
	if string(received.Data) != "existing-data" {
		t.Errorf("Data = %q, want %q", string(received.Data), "existing-data")
	}
}

func TestArtifactClient_OnUpdateFetch_FiredOnVersionChange(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.OnUpdateFetch("model", "", func(fetched *FetchedArtifact, err error) {
		callCount++
	})

	client.handleVersionChanged(`{"prefix":"plugins/owner","artifactName":"model","alias":"active","newVersion":"v2"}`)

	if callCount != 1 {
		t.Errorf("expected callback on version change, got %d", callCount)
	}
}

func TestArtifactClient_OnSharedUpdateFetch(t *testing.T) {
	var capturedPath string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.Write([]byte("shared-data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	var received *FetchedArtifact

	client.OnSharedUpdateFetch("shared-model", "", func(fetched *FetchedArtifact, err error) {
		received = fetched
	})

	client.FireStartupCallbacks()

	if capturedPath != "/api/v2/artifacts/shared/shared-model/versions/active" {
		t.Errorf("path = %q, want shared path", capturedPath)
	}
	if string(received.Data) != "shared-data" {
		t.Errorf("Data = %q", string(received.Data))
	}
}

func TestArtifactClient_FireStartupCallbacks_Incremental(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("data"))
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")

	calls1 := 0
	client.OnUpdateFetch("model-a", "", func(fetched *FetchedArtifact, err error) {
		calls1++
	})

	// First fire: should fire model-a callback
	client.FireStartupCallbacks()
	if calls1 != 1 {
		t.Errorf("after first fire, calls1 = %d, want 1", calls1)
	}

	// Register more callbacks
	calls2 := 0
	client.OnUpdateFetch("model-b", "", func(fetched *FetchedArtifact, err error) {
		calls2++
	})

	// Second fire: should only fire model-b, not model-a again
	client.FireStartupCallbacks()
	if calls1 != 1 {
		t.Errorf("after second fire, calls1 = %d, want 1 (should not re-fire)", calls1)
	}
	if calls2 != 1 {
		t.Errorf("after second fire, calls2 = %d, want 1", calls2)
	}
}

func TestArtifactClient_OnUpdateFetchWithDefault_PublishFails(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte("publish failed"))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	client := newArtifactClient(server.URL, "owner")
	client.SetCacheTTL(0)
	var receivedErr error

	client.OnUpdateFetchWithDefault("config.json", "",
		ArtifactDefault{Data: []byte("default"), ContentType: "text/plain"},
		func(fetched *FetchedArtifact, err error) {
			receivedErr = err
		})

	client.FireStartupCallbacks()

	if receivedErr == nil {
		t.Fatal("expected error when both fetch and publish fail")
	}
	if !strings.Contains(receivedErr.Error(), "default publish failed") {
		t.Errorf("error = %v, want mention of publish failure", receivedErr)
	}
}

func ctx() context.Context {
	return context.Background()
}
