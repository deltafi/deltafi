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
package org.deltafi.actionkit.artifact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.deltafi.common.artifact.VersionChangedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactCacheManagerTest {

    static final String PLUGIN = "my-plugin";
    static final String ARTIFACT = "my-model";
    static final String VERSION = "20240101-120000";
    static final byte[] BYTES = "model bytes".getBytes();
    static final ArtifactManifest MANIFEST = new ArtifactManifest(VERSION, "description", "application/octet-stream", "sha256abc", null);
    static final String SOURCE_PREFIX = "plugins/" + PLUGIN;
    static final VersionChangedEvent VERSION_EVENT = new VersionChangedEvent(SOURCE_PREFIX, ARTIFACT, "active", VERSION, "v2", Instant.now());

    @Mock
    ArtifactRegistryClient registryClient;
    ArtifactCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // No startPoller() call — tests stay single-threaded
        cacheManager = new ArtifactCacheManager(registryClient, new ArtifactRegistryCacheProperties());
    }

    @Test
    void resolve_pluginFirst_findsInPluginNamespace() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.getVersion()).isEqualTo(VERSION);
        assertThat(handle.isShared()).isFalse();
        assertThat(handle.getSourcePrefix()).isEqualTo(SOURCE_PREFIX);
        verify(registryClient, never()).download(eq("shared"), any(), any());
    }

    @Test
    void resolve_pluginFirst_fallsBackToShared() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(null);
        when(registryClient.download("shared", ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.isShared()).isTrue();
    }

    @Test
    void resolve_pluginOnly_doesNotFallBackToShared() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(null);

        assertThatThrownBy(() -> cacheManager.resolve(descriptor)).isInstanceOf(IllegalStateException.class).hasMessageContaining(ARTIFACT);

        verify(registryClient, never()).download(eq("shared"), any(), any());
    }

    @Test
    void resolve_sharedOnly_usesOnlySharedPrefix() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.SHARED_ONLY, true, "");

        when(registryClient.download("shared", ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.isShared()).isTrue();
        verify(registryClient, never()).download(eq(PLUGIN), any(), any());
    }

    @Test
    void resolve_sameArtifact_sharesBytes_notDuplicated() {
        // Two injection points for the same artifact — only one network call expected
        ArtifactDescriptor d1 = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        ArtifactDescriptor d2 = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle h1 = cacheManager.resolve(d1);
        ArtifactHandle h2 = cacheManager.resolve(d2);

        assertThat(h1.getBytes()).isEqualTo(BYTES);
        assertThat(h2.getBytes()).isEqualTo(BYTES);

        // Two downloads: one per resolve() call; no additional downloads on getBytes()
        verify(registryClient, times(2)).download(PLUGIN, ARTIFACT, "active");
    }

    // ── Caching behaviour ─────────────────────────────────────────────────────

    @Test
    void cacheable_getBytes_doesNotReFetch() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        // Repeated getBytes() must not trigger additional downloads
        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.getBytes()).isEqualTo(BYTES);

        verify(registryClient, times(1)).download(PLUGIN, ARTIFACT, "active");
    }

    @Test
    void nonCacheable_getBytes_alwaysDownloadsFresh() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, false, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        // Each getBytes() triggers a fresh download
        handle.getBytes();
        handle.getBytes();

        // 1 during resolve() + 2 for each getBytes() call
        verify(registryClient, times(3)).download(PLUGIN, ARTIFACT, "active");
    }

    @Test
    void nonCacheable_getBytes_updatesCacheForCacheableHandle() {
        byte[] freshBytes = "fresh bytes".getBytes();
        ArtifactManifest freshManifest = new ArtifactManifest("v2", "updated", "text/plain", "sha2", null);

        ArtifactDescriptor cached = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        ArtifactDescriptor uncached = descriptor(ArtifactScope.PLUGIN_ONLY, false, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))    // first resolve (cached)
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))    // second resolve (uncached)
                .thenReturn(new FetchedArtifact(freshManifest, SOURCE_PREFIX, freshBytes)); // getBytes() on uncached

        ArtifactHandle cacheableHandle = cacheManager.resolve(cached);
        ArtifactHandle uncachedHandle = cacheManager.resolve(uncached);

        // Non-cacheable handle fetches fresh, updating the shared cache
        byte[] fromUncached = uncachedHandle.getBytes();
        assertThat(fromUncached).isEqualTo(freshBytes);

        // Cacheable handle now reads the updated cache without a new download
        byte[] fromCached = cacheableHandle.getBytes();
        assertThat(fromCached).isEqualTo(freshBytes);
    }

    // ── Missing-artifact handling ─────────────────────────────────────────────

    @Test
    void resolve_missing_block_throwsIllegalState() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        when(registryClient.download(anyString(), anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> cacheManager.resolve(descriptor)).isInstanceOf(IllegalStateException.class).hasMessageContaining(ARTIFACT);
    }

    @Test
    void resolve_missing_seedDefault_returnsRealHandle() {
        String defaultResource = "artifact-test/default.bin";
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, defaultResource);
        when(registryClient.download(anyString(), anyString(), anyString())).thenReturn(null);

        ArtifactManifest seededManifest = new ArtifactManifest("20260319.171400.000", null,
                "application/octet-stream", "sha256abc", Map.of("default", "true"));
        when(registryClient.seedIfMissing(eq(PLUGIN), eq(ARTIFACT), eq("active"), any(byte[].class)))
                .thenReturn(seededManifest);

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        verify(registryClient).seedIfMissing(eq(PLUGIN), eq(ARTIFACT), eq("active"), any(byte[].class));

        assertThat(handle.isReady()).isTrue();
        assertThat(handle.getBytes()).isNotEmpty();
        assertThat(handle.getVersion()).isEqualTo("20260319.171400.000");
        assertThat(handle.getSha256()).isEqualTo("sha256abc");
        assertThat(handle.isDefault()).isTrue();
    }

    @Test
    void resolve_missing_seedDefault_throwsOnSeedFailure() {
        String defaultResource = "artifact-test/default.bin";
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, defaultResource);
        when(registryClient.download(anyString(), anyString(), anyString())).thenReturn(null);
        when(registryClient.seedIfMissing(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenReturn(null);

        assertThatThrownBy(() -> cacheManager.resolve(descriptor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to seed");
    }

    @Test
    void resolve_missing_seedDefault_supportsOnUpdate() {
        String defaultResource = "artifact-test/default.bin";
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, defaultResource);
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)  // resolve miss
                .thenReturn(new FetchedArtifact(
                        new ArtifactManifest("v2", "updated", "text/plain", "sha2", null),
                        SOURCE_PREFIX, "updated".getBytes()));

        ArtifactManifest seededManifest = new ArtifactManifest("v1", null,
                "application/octet-stream", "sha1", Map.of("default", "true"));
        when(registryClient.seedIfMissing(eq(PLUGIN), eq(ARTIFACT), eq("active"), any(byte[].class)))
                .thenReturn(seededManifest);

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> versions = new ArrayList<>();
        handle.onUpdate(event -> versions.add(event.newVersion()));

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);

        assertThat(versions).containsExactly("v2");
    }

    @Test
    void resolve_missing_seedDefault_throwsIfResourceMissing() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "nonexistent-resource.bin");
        when(registryClient.download(anyString(), anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> cacheManager.resolve(descriptor)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Default resource not found");
    }

    // ── refresh() ─────────────────────────────────────────────────────────────

    @Test
    void refresh_updatesSharedCacheAndAllHandles() {
        byte[] newBytes = "updated bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest("v2", "new desc", "text/plain", "newsha", null);

        ArtifactDescriptor d1 = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        ArtifactDescriptor d2 = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes)); // for refresh

        ArtifactHandle h1 = cacheManager.resolve(d1);
        ArtifactHandle h2 = cacheManager.resolve(d2);

        assertThat(h1.getVersion()).isEqualTo(VERSION);
        assertThat(h2.getVersion()).isEqualTo(VERSION);

        h1.refresh();

        assertThat(h1.getVersion()).isEqualTo("v2");
        assertThat(h2.getVersion()).isEqualTo("v2");  // shared cache updated
        assertThat(h1.getBytes()).isEqualTo(newBytes);
        assertThat(h2.getBytes()).isEqualTo(newBytes);
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    @Test
    void onUpdate_programmatic_receivesEventAndRefreshedBytes() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        byte[] newBytes = "new bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest("v2", "desc", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> calls.add(event.newVersion()));

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);

        assertThat(calls).containsExactly("v2");
        assertThat(handle.getBytes()).isEqualTo(newBytes);
    }

    @Test
    void nonCacheable_onUpdate_stillFiredOnPushNotification() {
        // cacheable = false means getBytes() always downloads fresh, but onUpdate callbacks
        // must still fire when a push notification arrives (Valkey path).
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, false, "");
        byte[] newBytes = "new bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest("v2", "desc", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> calls.add(event.newVersion()));

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);

        assertThat(calls).containsExactly("v2");
    }

    @Test
    void nonCacheable_onUpdate_stillFiredByPoll() {
        // cacheable = false: getFreshBytes() continuously updates the cache entry, but
        // lastNotifiedVersion is only advanced by notifyUpdate (push/poll paths). The poll
        // must still detect a drift and fire callbacks even after getFreshBytes has run.
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, false, "");
        byte[] freshBytes = "fresh".getBytes();
        ArtifactManifest v2Manifest = new ArtifactManifest("v2", "d", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))  // resolve
                .thenReturn(new FetchedArtifact(v2Manifest, SOURCE_PREFIX, freshBytes))  // getFreshBytes (simulates getBytes() call)
                .thenReturn(new FetchedArtifact(v2Manifest, SOURCE_PREFIX, freshBytes)); // poll re-fetch

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> versions = new ArrayList<>();
        handle.onUpdate(event -> versions.add(event.newVersion()));

        // Simulate a getBytes() call that updates the cache entry to v2 (via update, not notifyUpdate)
        // — lastNotifiedVersion stays at v1
        handle.getBytes();

        // Poll sees v2 remotely; lastNotifiedVersion is still v1, so versionHasDiverged = true
        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry(ARTIFACT, Map.of("active", "v2")))));

        cacheManager.pollForChanges();

        assertThat(versions).containsExactly("v2");
    }

    @Test
    void onUpdate_callbackNotFiredForDifferentOwner() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> calls.add("fired"));

        cacheManager.invokeCallbacks("shared", ARTIFACT, "active", VERSION_EVENT);
        assertThat(calls).isEmpty();

        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);
        assertThat(calls).containsExactly("fired");
    }

    @Test
    void onUpdate_multipleCallbacks_allInvoked() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> calls.add("cb1"));
        handle.onUpdate(event -> calls.add("cb2"));

        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);
        assertThat(calls).containsExactly("cb1", "cb2");
    }

    @Test
    void onUpdate_failingCallback_doesNotPreventOthers() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> { throw new RuntimeException("boom"); });
        handle.onUpdate(event -> calls.add("survived"));

        assertThatCode(() -> cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT))
                .doesNotThrowAnyException();
        assertThat(calls).containsExactly("survived");
    }

    @Test
    void onUpdate_runnableConvenienceOverload_isSupported() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> calls = new ArrayList<>();
        handle.onUpdate(() -> calls.add("runnable"));

        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active", VERSION_EVENT);
        assertThat(calls).containsExactly("runnable");
    }

    @Test
    void invokeCallbacks_missingKey_doesNotThrow() {
        assertThatCode(() -> cacheManager.invokeCallbacks(PLUGIN, "unknown-artifact", "active", VERSION_EVENT))
                .doesNotThrowAnyException();
    }

    // ── Pinned entries (concrete version alias) ───────────────────────────────

    @Test
    void resolve_withConcreteVersionAlias_isPinnedAndNeverReFetched() {
        // When the alias equals the returned manifest version, the entry is pinned
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                PLUGIN, ARTIFACT, VERSION, ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, VERSION))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        assertThat(handle.getBytes()).isEqualTo(BYTES);
        // Repeated getBytes() must not trigger additional downloads
        handle.getBytes();
        handle.getBytes();
        verify(registryClient, times(1)).download(PLUGIN, ARTIFACT, VERSION);
    }

    @Test
    void refreshIfCached_pinnedEntry_redownloadsOnExplicitEvent() {
        // Valkey version-change events are always honoured, even for pinned entries
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                PLUGIN, ARTIFACT, VERSION, ArtifactScope.PLUGIN_ONLY, true, "");

        byte[] newBytes = "replaced bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest(VERSION, "replaced", "application/octet-stream", "newsha", null);

        when(registryClient.download(PLUGIN, ARTIFACT, VERSION))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.getBytes()).isEqualTo(BYTES);

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, VERSION);

        verify(registryClient, times(2)).download(PLUGIN, ARTIFACT, VERSION);
        assertThat(handle.getBytes()).isEqualTo(newBytes);
    }

    @Test
    void resolve_withAliasAlias_isNotPinned() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, false, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        cacheManager.resolve(descriptor);

        byte[] fresh = "fresh".getBytes();
        ArtifactManifest freshManifest = new ArtifactManifest("v2", "d", "text/plain", "sha2", null);
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(freshManifest, SOURCE_PREFIX, fresh));

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");

        verify(registryClient, times(2)).download(PLUGIN, ARTIFACT, "active");
    }

    // ── Background poll ───────────────────────────────────────────────────────

    @Test
    void pollForChanges_detectsVersionChange_refreshesEntry() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        byte[] newBytes = "new bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest("v2", "new", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))  // resolve
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes)); // poll re-fetch

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.getVersion()).isEqualTo(VERSION);

        // List response shows alias → "v2" (different from cached VERSION)
        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry(ARTIFACT, Map.of("active", "v2")))));

        cacheManager.pollForChanges();

        assertThat(handle.getVersion()).isEqualTo("v2");
        assertThat(handle.getBytes()).isEqualTo(newBytes);
        verify(registryClient, times(2)).download(PLUGIN, ARTIFACT, "active");
    }

    @Test
    void pollForChanges_noVersionChange_doesNotRefetch() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        cacheManager.resolve(descriptor);

        // List response shows same version — no change
        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry(ARTIFACT, Map.of("active", VERSION)))));

        cacheManager.pollForChanges();

        // Only one download: the initial resolve()
        verify(registryClient, times(1)).download(PLUGIN, ARTIFACT, "active");
    }

    @Test
    void pollForChanges_pinnedEntries_areNotPolled() {
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                PLUGIN, ARTIFACT, VERSION, ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, VERSION))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        cacheManager.resolve(descriptor);

        // pollForChanges must not call listArtifacts when only pinned entries are cached
        cacheManager.pollForChanges();

        verify(registryClient, never()).listArtifacts(any());
    }

    @Test
    void pollForChanges_invokeCallbacks_onVersionChange() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        byte[] newBytes = "new bytes".getBytes();
        ArtifactManifest newManifest = new ArtifactManifest("v2", "new", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))
                .thenReturn(new FetchedArtifact(newManifest, SOURCE_PREFIX, newBytes));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> versions = new ArrayList<>();
        handle.onUpdate(event -> versions.add(event.newVersion()));

        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry(ARTIFACT, Map.of("active", "v2")))));

        cacheManager.pollForChanges();

        assertThat(versions).containsExactly("v2");
    }

    // ── Cache eviction ────────────────────────────────────────────────────────

    @Test
    void eviction_maxEntries_evictsOldestNonPinned() {
        ArtifactRegistryCacheProperties props = new ArtifactRegistryCacheProperties();
        props.setMaxEntries(2);
        props.setMaxBytes(0);
        cacheManager = new ArtifactCacheManager(registryClient, props);

        for (int i = 1; i <= 3; i++) {
            String name = "artifact-" + i;
            ArtifactManifest m = new ArtifactManifest(VERSION, "d", "text/plain", "sha", null);
            when(registryClient.download(PLUGIN, name, "active"))
                    .thenReturn(new FetchedArtifact(m, SOURCE_PREFIX, BYTES));
            ArtifactDescriptor d = new ArtifactDescriptor(
                    PLUGIN, name, "active", ArtifactScope.PLUGIN_ONLY, true, "");
            cacheManager.resolve(d);
        }

        long cachedCount = 0;
        for (int i = 1; i <= 3; i++) {
            if (cacheManager.getCurrent(PLUGIN, "artifact-" + i, "active") != null) cachedCount++;
        }
        assertThat(cachedCount).isLessThanOrEqualTo(2);
    }

    @Test
    void eviction_maxBytes_evictsOldestNonPinned() {
        ArtifactRegistryCacheProperties props = new ArtifactRegistryCacheProperties();
        props.setMaxEntries(0);
        props.setMaxBytes(BYTES.length);
        cacheManager = new ArtifactCacheManager(registryClient, props);

        for (int i = 1; i <= 2; i++) {
            String name = "artifact-" + i;
            ArtifactManifest m = new ArtifactManifest(VERSION, "d", "text/plain", "sha", null);
            when(registryClient.download(PLUGIN, name, "active"))
                    .thenReturn(new FetchedArtifact(m, SOURCE_PREFIX, BYTES));
            ArtifactDescriptor d = new ArtifactDescriptor(
                    PLUGIN, name, "active", ArtifactScope.PLUGIN_ONLY, true, "");
            cacheManager.resolve(d);
        }

        long remaining = 0;
        for (int i = 1; i <= 2; i++) {
            if (cacheManager.getCurrent(PLUGIN, "artifact-" + i, "active") != null) remaining++;
        }
        assertThat(remaining).isLessThanOrEqualTo(1);
    }

    @Test
    void eviction_pinnedEntries_areNotEvicted() {
        ArtifactRegistryCacheProperties props = new ArtifactRegistryCacheProperties();
        props.setMaxEntries(1);
        props.setMaxBytes(0);
        cacheManager = new ArtifactCacheManager(registryClient, props);

        ArtifactManifest m = new ArtifactManifest(VERSION, "d", "text/plain", "sha", null);
        when(registryClient.download(PLUGIN, ARTIFACT, VERSION))
                .thenReturn(new FetchedArtifact(m, SOURCE_PREFIX, BYTES));
        ArtifactDescriptor pinned = new ArtifactDescriptor(
                PLUGIN, ARTIFACT, VERSION, ArtifactScope.PLUGIN_ONLY, true, "");
        cacheManager.resolve(pinned);

        String other = "other-artifact";
        when(registryClient.download(PLUGIN, other, "active"))
                .thenReturn(new FetchedArtifact(m, SOURCE_PREFIX, BYTES));
        ArtifactDescriptor unpinned = new ArtifactDescriptor(
                PLUGIN, other, "active", ArtifactScope.PLUGIN_ONLY, true, "");
        cacheManager.resolve(unpinned);

        // The pinned entry should still be in the cache (non-pinned was evicted)
        assertThat(cacheManager.getCurrent(PLUGIN, ARTIFACT, VERSION)).isNotNull();
    }

    // ── PLUGIN_FIRST promotion (shared → plugin-specific) ─────────────────────

    @Test
    void refreshIfCached_pluginFirst_promotesSharedEntryWhenPluginVersionPublished() {
        // Resolve via PLUGIN_FIRST: plugin namespace absent, falls back to shared
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");
        byte[] pluginBytes = "plugin bytes".getBytes();
        ArtifactManifest pluginManifest = new ArtifactManifest("v2", "plugin version", "application/octet-stream", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)  // initial resolve — plugin namespace absent
                .thenReturn(new FetchedArtifact(pluginManifest, SOURCE_PREFIX, pluginBytes)); // promotion download
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.isShared()).isTrue();
        assertThat(handle.getBytes()).isEqualTo(BYTES);

        // Operator publishes a plugin-specific version — event arrives for the plugin owner
        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");

        // Existing handle serves the plugin-specific bytes without restart
        assertThat(handle.getBytes()).isEqualTo(pluginBytes);
        assertThat(handle.isShared()).isFalse();
        assertThat(handle.getVersion()).isEqualTo("v2");
    }

    @Test
    void refreshIfCached_pluginFirst_firesCallbacksOnPromotion() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");
        byte[] pluginBytes = "plugin bytes".getBytes();
        ArtifactManifest pluginManifest = new ArtifactManifest("v2", "d", "text/plain", "sha2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)
                .thenReturn(new FetchedArtifact(pluginManifest, SOURCE_PREFIX, pluginBytes));
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);

        List<String> versions = new ArrayList<>();
        handle.onUpdate(event -> versions.add(event.newVersion()));

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        // In production, handleMessage calls invokeCallbacks after refreshIfCached.
        cacheManager.invokeCallbacks(PLUGIN, ARTIFACT, "active",
                new VersionChangedEvent(SOURCE_PREFIX, ARTIFACT, "active", VERSION, "v2", java.time.Instant.now()));

        assertThat(versions).containsExactly("v2");
    }

    @Test
    void refreshIfCached_sharedOnly_doesNotPromoteOnPluginEvent() {
        // SHARED_ONLY scope: a plugin-owner event must not touch the shared entry
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.SHARED_ONLY, true, "");

        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.isShared()).isTrue();

        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");

        verify(registryClient, never()).download(eq(PLUGIN), any(), any());
        assertThat(handle.getBytes()).isEqualTo(BYTES);
    }

    @Test
    void pollForChanges_afterPromotion_pollsPluginNamespaceNotShared() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");
        byte[] pluginBytes = "plugin bytes".getBytes();
        ArtifactManifest v2 = new ArtifactManifest("v2", "d", "text/plain", "sha2", null);
        byte[] v3Bytes = "v3 bytes".getBytes();
        ArtifactManifest v3 = new ArtifactManifest("v3", "d", "text/plain", "sha3", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)                                                          // initial resolve
                .thenReturn(new FetchedArtifact(v2, SOURCE_PREFIX, pluginBytes))          // promotion
                .thenReturn(new FetchedArtifact(v3, SOURCE_PREFIX, v3Bytes));             // poll re-fetch
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, "shared", BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active"); // triggers promotion

        // Poll: plugin namespace reports v3
        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry(ARTIFACT, Map.of("active", "v3")))));

        cacheManager.pollForChanges();

        assertThat(handle.getVersion()).isEqualTo("v3");
        assertThat(handle.getBytes()).isEqualTo(v3Bytes);
        verify(registryClient, never()).listArtifacts("shared");
    }

    // ── SHARED_ONLY + PLUGIN_FIRST coexistence ────────────────────────────────

    @Test
    void sharedOnly_and_pluginFirst_coexist_independently_sharedOnlyResolvedFirst() {
        // Order 1: SHARED_ONLY resolves first, then PLUGIN_FIRST falls back to shared.
        // Each scope gets its own cache entry: ("shared", …) for SHARED_ONLY and
        // ("my-plugin", …) for PLUGIN_FIRST (backedByShared = true).
        //
        // Phase A — shared update propagates to both entries via propagateSharedUpdate.
        // Phase B — plugin version published: PLUGIN_FIRST promotes to plugin bytes;
        //           SHARED_ONLY entry is untouched (different cache key).
        ArtifactDescriptor sharedOnlyDesc = descriptor(ArtifactScope.SHARED_ONLY, true, "");
        ArtifactDescriptor pluginFirstDesc = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");

        byte[] sharedV1 = "shared-v1 bytes".getBytes();
        byte[] sharedV2 = "shared-v2 bytes".getBytes();
        byte[] pluginBytes = "plugin bytes".getBytes();
        ArtifactManifest manifestV1 = new ArtifactManifest("shared-v1", "shared", "text/plain", "sha-s1", null);
        ArtifactManifest manifestV2 = new ArtifactManifest("shared-v2", "shared", "text/plain", "sha-s2", null);
        ArtifactManifest pluginManifest = new ArtifactManifest("plugin-v1", "plugin", "text/plain", "sha-p1", null);

        // PLUGIN_FIRST tries plugin namespace (null) then falls back to shared.
        // Second PLUGIN call is the promotion download.
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)                                                              // PLUGIN_FIRST initial
                .thenReturn(new FetchedArtifact(pluginManifest, SOURCE_PREFIX, pluginBytes)); // promotion

        // shared: SHARED_ONLY resolve, then shared-entry refresh.
        // PLUGIN_FIRST fallback reuses the already-cached ("shared",…) entry — no extra download.
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(manifestV1, "shared", sharedV1))  // SHARED_ONLY resolve
                .thenReturn(new FetchedArtifact(manifestV2, "shared", sharedV2)); // shared entry refresh

        ArtifactHandle sharedHandle = cacheManager.resolve(sharedOnlyDesc);
        ArtifactHandle pluginFirstHandle = cacheManager.resolve(pluginFirstDesc);

        // Initial state: both serve shared-v1; PLUGIN_FIRST is backed by shared
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV1);
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(sharedV1);
        assertThat(pluginFirstHandle.isShared()).isTrue();

        // Phase A: shared update — ("shared",…) entry refreshed, propagated to ("my-plugin",…)
        cacheManager.refreshIfCached("shared", ARTIFACT, "active");
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV2);
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(sharedV2); // received via propagation

        // Phase B: plugin version published — PLUGIN_FIRST promotes, SHARED_ONLY unaffected
        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(pluginBytes);
        assertThat(pluginFirstHandle.isShared()).isFalse(); // promoted to plugin namespace
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV2); // SHARED_ONLY entry untouched
        assertThat(sharedHandle.isShared()).isTrue();
    }

    @Test
    void sharedOnly_and_pluginFirst_coexist_independently_pluginFirstResolvedFirst() {
        // Order 2: PLUGIN_FIRST resolves first (falls back to shared), then SHARED_ONLY resolves.
        // Each scope still gets its own independent cache entry — the resolve order does not
        // change the outcome.
        ArtifactDescriptor pluginFirstDesc = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");
        ArtifactDescriptor sharedOnlyDesc = descriptor(ArtifactScope.SHARED_ONLY, true, "");

        byte[] sharedV1 = "shared-v1 bytes".getBytes();
        byte[] sharedV2 = "shared-v2 bytes".getBytes();
        byte[] pluginBytes = "plugin bytes".getBytes();
        ArtifactManifest manifestV1 = new ArtifactManifest("shared-v1", "shared", "text/plain", "sha-s1", null);
        ArtifactManifest manifestV2 = new ArtifactManifest("shared-v2", "shared", "text/plain", "sha-s2", null);
        ArtifactManifest pluginManifest = new ArtifactManifest("plugin-v1", "plugin", "text/plain", "sha-p1", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(null)                                                              // PLUGIN_FIRST initial
                .thenReturn(new FetchedArtifact(pluginManifest, SOURCE_PREFIX, pluginBytes)); // promotion

        // shared: PLUGIN_FIRST fallback, then shared-entry refresh.
        // SHARED_ONLY resolve reuses the backedByShared PLUGIN_FIRST entry — no extra download.
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(manifestV1, "shared", sharedV1))  // PLUGIN_FIRST fallback
                .thenReturn(new FetchedArtifact(manifestV2, "shared", sharedV2)); // shared entry refresh

        ArtifactHandle pluginFirstHandle = cacheManager.resolve(pluginFirstDesc);
        ArtifactHandle sharedHandle = cacheManager.resolve(sharedOnlyDesc);

        // Initial state: both serve shared-v1
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(sharedV1);
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV1);
        assertThat(pluginFirstHandle.isShared()).isTrue();

        // Phase A: shared update — propagates to PLUGIN_FIRST entry
        cacheManager.refreshIfCached("shared", ARTIFACT, "active");
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV2);
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(sharedV2);

        // Phase B: plugin version published — PLUGIN_FIRST promotes, SHARED_ONLY unaffected
        cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active");
        assertThat(pluginFirstHandle.getBytes()).isEqualTo(pluginBytes);
        assertThat(pluginFirstHandle.isShared()).isFalse();
        assertThat(sharedHandle.getBytes()).isEqualTo(sharedV2);
        assertThat(sharedHandle.isShared()).isTrue();
    }

    @Test
    void sharedUpdate_propagatesToPluginFirstHandleBackedByShared() {
        // PLUGIN_FIRST only (no SHARED_ONLY) falls back to shared.
        // When the shared artifact is updated, refreshIfCached("shared", …) propagates
        // the new bytes to the plugin-keyed entry via propagateSharedUpdate, and fires
        // the registered callback on the plugin owner key.
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_FIRST, true, "");

        byte[] sharedV1 = "shared-v1".getBytes();
        byte[] sharedV2 = "shared-v2".getBytes();
        ArtifactManifest manifestV1 = new ArtifactManifest("shared-v1", "shared", "text/plain", "sha-s1", null);
        ArtifactManifest manifestV2 = new ArtifactManifest("shared-v2", "shared", "text/plain", "sha-s2", null);

        when(registryClient.download(PLUGIN, ARTIFACT, "active")).thenReturn(null); // plugin absent
        when(registryClient.download("shared", ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(manifestV1, "shared", sharedV1))  // fallback
                .thenReturn(new FetchedArtifact(manifestV2, "shared", sharedV2)); // propagation download

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.getBytes()).isEqualTo(sharedV1);
        assertThat(handle.isShared()).isTrue();

        List<String> versions = new ArrayList<>();
        handle.onUpdate(event -> versions.add(event.newVersion()));

        // No ("shared",…) cache entry exists — propagateSharedUpdate downloads from shared
        cacheManager.refreshIfCached("shared", ARTIFACT, "active");

        assertThat(handle.getBytes()).isEqualTo(sharedV2);
        assertThat(versions).containsExactly("shared-v2");
    }

    // ── Deleted artifact handling ──────────────────────────────────────────────

    @Test
    void refreshIfCached_artifactDeleted_retainsCachedBytes() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES))  // resolve
                .thenReturn(null); // artifact deleted

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.getBytes()).isEqualTo(BYTES);

        assertThatCode(() -> cacheManager.refreshIfCached(PLUGIN, ARTIFACT, "active"))
                .doesNotThrowAnyException();

        // Cached bytes and version must be retained
        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.getVersion()).isEqualTo(VERSION);
    }

    @Test
    void pollForChanges_artifactDeletedFromRegistry_retainsCachedBytes() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");

        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        assertThat(handle.getBytes()).isEqualTo(BYTES);

        // listArtifacts returns a non-empty response for the owner but this artifact is absent
        when(registryClient.listArtifacts(PLUGIN)).thenReturn(
                new ArtifactRegistryClient.ArtifactListResponse(List.of(
                        new ArtifactRegistryClient.ArtifactSummaryEntry("other-artifact",
                                Map.of("active", "v1")))));

        assertThatCode(() -> cacheManager.pollForChanges()).doesNotThrowAnyException();

        // Bytes and version retained; no re-download attempted
        assertThat(handle.getBytes()).isEqualTo(BYTES);
        assertThat(handle.getVersion()).isEqualTo(VERSION);
        verify(registryClient, times(1)).download(any(), any(), any()); // only the initial resolve
    }

    // ── Startup callbacks ─────────────────────────────────────────────────────

    @Test
    void fireStartupCallbacks_invokesAllRegistered() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        List<VersionChangedEvent> events = new ArrayList<>();
        handle.onUpdate(events::add);

        cacheManager.fireStartupCallbacks();

        assertThat(events).hasSize(1);
        VersionChangedEvent evt = events.getFirst();
        assertThat(evt.previousVersion()).isNull();
        assertThat(evt.newVersion()).isEqualTo(VERSION);
        assertThat(evt.artifactName()).isEqualTo(ARTIFACT);
        assertThat(evt.alias()).isEqualTo("active");
    }

    @Test
    void fireStartupCallbacks_noCallbacksIsNoop() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        cacheManager.resolve(descriptor);

        assertThatCode(() -> cacheManager.fireStartupCallbacks()).doesNotThrowAnyException();
    }

    @Test
    void fireStartupCallbacks_failingCallbackDoesNotBlockOthers() {
        ArtifactDescriptor descriptor = descriptor(ArtifactScope.PLUGIN_ONLY, true, "");
        when(registryClient.download(PLUGIN, ARTIFACT, "active"))
                .thenReturn(new FetchedArtifact(MANIFEST, SOURCE_PREFIX, BYTES));

        ArtifactHandle handle = cacheManager.resolve(descriptor);
        List<String> calls = new ArrayList<>();
        handle.onUpdate(event -> { throw new RuntimeException("boom"); });
        handle.onUpdate(event -> calls.add("survived"));

        cacheManager.fireStartupCallbacks();

        assertThat(calls).containsExactly("survived");
    }

    private ArtifactDescriptor descriptor(ArtifactScope scope, boolean cacheable, String defaultResource) {
        return new ArtifactDescriptor(PLUGIN, ARTIFACT, "active", scope, cacheable, defaultResource);
    }
}
