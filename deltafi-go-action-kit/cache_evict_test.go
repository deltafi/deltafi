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
// ABOUTME: Tests for cache expiry eviction, SafeStringMap.Get, and ActionContext.SaveFromReader.
// ABOUTME: Covers small but previously untested helpers on hot-adjacent paths.

package actionkit

import (
	"strings"
	"testing"
	"time"
)

func TestLookupClient_EvictExpired(t *testing.T) {
	lc := newLookupClient("http://core")
	lc.cache["expired"] = &lookupCacheEntry{expiresAt: time.Now().Add(-time.Minute)}
	lc.cache["fresh"] = &lookupCacheEntry{expiresAt: time.Now().Add(time.Minute)}

	lc.evictExpired()

	if _, ok := lc.cache["expired"]; ok {
		t.Error("expired entry should have been evicted")
	}
	if _, ok := lc.cache["fresh"]; !ok {
		t.Error("fresh entry should have been retained")
	}
}

func TestArtifactClient_EvictExpired(t *testing.T) {
	ac := newArtifactClient("http://core", "owner")
	ac.cache["expired"] = &artifactCacheEntry{expiresAt: time.Now().Add(-time.Minute)}
	ac.cache["fresh"] = &artifactCacheEntry{expiresAt: time.Now().Add(time.Minute)}

	ac.evictExpired()

	if _, ok := ac.cache["expired"]; ok {
		t.Error("expired entry should have been evicted")
	}
	if _, ok := ac.cache["fresh"]; !ok {
		t.Error("fresh entry should have been retained")
	}
}

func TestSafeStringMap_Get(t *testing.T) {
	var sm SafeStringMap
	sm.Store(map[string]string{"key": "value"})

	if v, ok := sm.Get("key"); !ok || v != "value" {
		t.Errorf("Get(key) = %q, %v; want value, true", v, ok)
	}
	if v, ok := sm.Get("missing"); ok || v != "" {
		t.Errorf("Get(missing) = %q, %v; want \"\", false", v, ok)
	}
}

func TestActionContext_SaveFromReader(t *testing.T) {
	tc := NewTestContext("did-1")

	content, err := tc.SaveFromReader(tc.ctx, "f.txt", "text/plain", strings.NewReader("streamed"))
	if err != nil {
		t.Fatalf("SaveFromReader: %v", err)
	}

	got, err := content.LoadString()
	if err != nil {
		t.Fatalf("LoadString: %v", err)
	}
	if got != "streamed" {
		t.Errorf("round-trip = %q, want streamed", got)
	}
}
