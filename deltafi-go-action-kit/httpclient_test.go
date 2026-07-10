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
// ABOUTME: Tests for HTTPClientParams.BuildClient proxy resolution and caching.
// ABOUTME: Verifies no-proxy reuse of the platform client and per-proxy client caching.

package actionkit

import (
	"net/http"
	"testing"
)

func TestBuildClient_NoProxyReusesPlatformClient(t *testing.T) {
	p := &HTTPClientParams{}
	c, err := p.BuildClient()
	if err != nil {
		t.Fatalf("BuildClient: %v", err)
	}
	if c != platformClient {
		t.Error("with no proxy, BuildClient should return the shared platform client")
	}
}

func TestBuildClient_ProxyClientIsCached(t *testing.T) {
	p := &HTTPClientParams{ProxyURL: "http://proxy.example.test:8080"}

	c1, err := p.BuildClient()
	if err != nil {
		t.Fatalf("BuildClient: %v", err)
	}
	if c1 == platformClient {
		t.Error("proxy client should differ from the platform client")
	}

	c2, err := p.BuildClient()
	if err != nil {
		t.Fatalf("BuildClient (second): %v", err)
	}
	if c1 != c2 {
		t.Error("repeated BuildClient with the same proxy should return the cached client")
	}
}

func TestBuildClient_InvalidProxyURL(t *testing.T) {
	p := &HTTPClientParams{ProxyURL: "://missing-scheme"}
	if _, err := p.BuildClient(); err == nil {
		t.Error("expected an error for an invalid proxy URL")
	}
}

func TestBuildPlatformTransport_PreservesDefaultTransportBehavior(t *testing.T) {
	transport, err := buildPlatformTransport()
	if err != nil {
		t.Fatalf("buildPlatformTransport: %v", err)
	}

	defaultTransport := http.DefaultTransport.(*http.Transport)
	if transport == defaultTransport {
		t.Fatal("buildPlatformTransport should clone DefaultTransport, not mutate it")
	}
	if transport.Proxy == nil {
		t.Fatal("Proxy should be preserved from DefaultTransport")
	}
	if transport.DialContext == nil {
		t.Fatal("DialContext should be preserved from DefaultTransport")
	}
	if transport.TLSHandshakeTimeout != defaultTransport.TLSHandshakeTimeout {
		t.Fatalf("TLSHandshakeTimeout = %v, want %v", transport.TLSHandshakeTimeout, defaultTransport.TLSHandshakeTimeout)
	}
	if transport.IdleConnTimeout != defaultTransport.IdleConnTimeout {
		t.Fatalf("IdleConnTimeout = %v, want %v", transport.IdleConnTimeout, defaultTransport.IdleConnTimeout)
	}
	if transport.ForceAttemptHTTP2 != defaultTransport.ForceAttemptHTTP2 {
		t.Fatalf("ForceAttemptHTTP2 = %v, want %v", transport.ForceAttemptHTTP2, defaultTransport.ForceAttemptHTTP2)
	}
	if transport.ResponseHeaderTimeout != platformResponseHeaderTimeout {
		t.Fatalf("ResponseHeaderTimeout = %v, want %v", transport.ResponseHeaderTimeout, platformResponseHeaderTimeout)
	}
	if transport.MaxIdleConnsPerHost != platformMaxIdleConnsPerHost {
		t.Fatalf("MaxIdleConnsPerHost = %d, want %d", transport.MaxIdleConnsPerHost, platformMaxIdleConnsPerHost)
	}
}
