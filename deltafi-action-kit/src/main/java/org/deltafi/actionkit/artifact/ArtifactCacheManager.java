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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.artifact.VersionChangedEvent;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Centralized cache for resolved artifact bytes, keyed by (owner, artifactName, alias).
 *
 * <p>Multiple injection points that resolve the same artifact share a single {@link CacheEntry},
 * avoiding duplicate in-memory copies of the blob.
 *
 * <p>{@link ArtifactScope#PLUGIN_FIRST} entries are always keyed by the plugin owner, even when
 * they fell back to the shared namespace. This decouples them from {@link ArtifactScope#SHARED_ONLY}
 * entries, allowing each to evolve independently. A {@code volatile boolean backedByShared} flag
 * marks PLUGIN_FIRST entries that are currently serving bytes from the shared namespace; the flag
 * is cleared on promotion to the plugin-specific namespace.
 *
 * <p>Cached entries stay current through two complementary mechanisms:
 * <ol>
 *   <li><b>Push</b> — version-change notifications from the platform message bus arrive via
 *       {@link #refreshIfCached} and immediately update the affected entry.
 *   <li><b>Poll</b> — a background thread periodically calls {@link #pollForChanges()} to
 *       compare each non-pinned entry against the registry's current alias mapping and
 *       re-fetches any that have drifted. This catches notifications that were missed due to
 *       transient network or broker failures.
 * </ol>
 *
 * <p>Non-cacheable handles ({@link PluginArtifact#cacheable()} = {@code false}, the default)
 * always download fresh bytes on every {@code getBytes()} call and update the shared cache as a
 * side effect, keeping it current for any co-located cacheable handles.
 *
 * <p>Pinned entries ({@link PluginArtifact#alias()} equals a concrete version string) are never
 * polled for changes — artifact versions are immutable. They do, however, still respond to
 * explicit version-change notifications (the push path) in case an artifact was deleted and
 * recreated at the same version string.
 *
 * <p>Thread safety: {@link CacheEntry} stores its data atomically; the cache map uses
 * {@link ConcurrentHashMap}.
 */
@Slf4j
class ArtifactCacheManager implements AutoCloseable {

    private record CacheKey(String owner, String artifactName, String alias) {}

    private static final class CacheEntry {
        private record Snapshot(FetchedArtifact artifact, Instant fetchedAt) {}

        /** Atomic pair of (artifact, fetchedAt). Written as a unit so readers always see a consistent snapshot. */
        private final AtomicReference<Snapshot> data;

        /**
         * When {@code true}, this entry was resolved using a concrete version string (not an alias).
         * Artifact versions are immutable, so pinned entries are never polled for version drift,
         * but they still respond to explicit version-change notifications.
         */
        private final boolean pinned;

        /**
         * The owner namespace to use for all downloads and registry polls against this entry.
         * For {@link ArtifactScope#PLUGIN_FIRST} entries that initially resolved from the shared
         * namespace, this starts as {@code "shared"} and is promoted to the plugin namespace when
         * a plugin-specific version is published ({@code backedByShared} is cleared at that point).
         */
        volatile String effectiveOwner;

        /**
         * When {@code true}, this is a {@link ArtifactScope#PLUGIN_FIRST} entry that is currently
         * serving bytes from the shared namespace because the plugin namespace had no version at
         * resolve time. Cleared on promotion to the plugin-specific namespace.
         *
         * <p>Entries with this flag set receive shared-namespace updates via
         * {@link ArtifactCacheManager#propagateSharedUpdate}.
         */
        volatile boolean backedByShared;

        CacheEntry(FetchedArtifact artifact, boolean pinned, String initialOwner, boolean backedByShared) {
            this.data = new AtomicReference<>(new Snapshot(artifact, Instant.now()));
            this.pinned = pinned;
            this.effectiveOwner = initialOwner;
            this.backedByShared = backedByShared;
            this.lastNotifiedVersion = artifact.manifest().version();
        }

        boolean isPinned() {
            return pinned;
        }

        FetchedArtifact getArtifact() {
            return data.get().artifact();
        }

        /** Returns the timestamp of the most recent fetch, used for LRU eviction ordering. */
        Instant lastFetchedAt() {
            return data.get().fetchedAt();
        }

        /** Returns the size of the cached bytes, used for total-bytes eviction checks. */
        int byteCount() {
            return data.get().artifact().bytes().length;
        }

        /**
         * The version string last advanced by a push notification or poll event.
         * Unlike {@link #data}, this is NOT updated by {@link ArtifactCacheManager#getFreshBytes}
         * so that the poll can still detect drift even when non-cacheable handles are continuously
         * keeping the cache entry current with fresh downloads.
         */
        volatile String lastNotifiedVersion;

        /** Atomically replace the stored artifact and timestamp (used by getFreshBytes). */
        void update(FetchedArtifact fresh) {
            data.set(new Snapshot(fresh, Instant.now()));
        }

        /**
         * Replace the stored artifact AND advance {@code lastNotifiedVersion}.
         * Called by push (refreshIfCached) and poll (pollForChanges, propagateSharedUpdate)
         * paths so that {@link ArtifactCacheManager#versionHasDiverged} can track drift
         * independently of non-cacheable handle downloads.
         */
        void notifyUpdate(FetchedArtifact fresh) {
            data.set(new Snapshot(fresh, Instant.now()));
            lastNotifiedVersion = fresh.manifest().version();
        }
    }

    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    /** Keyed by the same (owner, artifactName, alias) triple as the cache. */
    private final ConcurrentHashMap<CacheKey, CopyOnWriteArrayList<Consumer<VersionChangedEvent>>> callbacks =
            new ConcurrentHashMap<>();
    private final ArtifactRegistryClient client;
    private final ArtifactCacheConfig props;
    private final ScheduledExecutorService pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "artifact-cache-poll");
        t.setDaemon(true);
        return t;
    });

    ArtifactCacheManager(ArtifactRegistryClient client, ArtifactCacheConfig props) {
        this.client = client;
        this.props = props;
    }

    /**
     * Starts the background polling loop. Called once by {@link ArtifactInfrastructureLifecycle}
     * when the artifact infrastructure starts. Not called in tests so they remain single-threaded.
     */
    void startPoller() {
        int intervalSeconds = props.getPollIntervalSeconds();
        if (intervalSeconds > 0) {
            pollScheduler.scheduleWithFixedDelay(
                    this::pollForChanges,
                    intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
            log.debug("Artifact cache poll started: interval={}s", intervalSeconds);
        }
    }

    @Override
    public void close() {
        pollScheduler.shutdownNow();
    }

    /**
     * Resolve the artifact described by {@code descriptor} and return a handle to it.
     *
     * <p>Owners are tried in scope order. The first successful download wins. If the artifact
     * is already in the shared cache from a prior injection, the cache entry is updated with
     * fresh data.
     *
     * <p>{@link ArtifactScope#PLUGIN_FIRST} entries are always keyed by the plugin owner, even
     * when they fall back to the shared namespace. This ensures that a shared-namespace update
     * cannot overwrite plugin-specific bytes, and that a plugin publish can always promote the
     * entry without touching any {@link ArtifactScope#SHARED_ONLY} entry.
     *
     * @throws IllegalStateException if the artifact is not found and no {@code defaultResource}
     *                               is configured for seeding
     */
    ArtifactHandle resolve(ArtifactDescriptor descriptor) {
        for (String owner : ownersFor(descriptor)) {
            FetchedArtifact fetched = reuseOrDownload(owner, descriptor.artifactName(), descriptor.alias());
            if (fetched != null) {
                // PLUGIN_FIRST entries are always keyed by the plugin owner so they remain
                // independent of any SHARED_ONLY entry that happens to use the same artifact.
                String keyOwner = descriptor.scope() == ArtifactScope.PLUGIN_FIRST
                        ? descriptor.pluginName() : owner;
                boolean backedByShared = descriptor.scope() == ArtifactScope.PLUGIN_FIRST
                        && ArtifactRegistryConstants.SHARED_OWNER.equals(owner);

                CacheKey key = new CacheKey(keyOwner, descriptor.artifactName(), descriptor.alias());
                // A descriptor whose alias equals the resolved version is pointing at a concrete,
                // immutable version — mark the entry as pinned so it is never polled for drift.
                boolean pinned = descriptor.alias().equals(fetched.manifest().version());

                cache.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return new CacheEntry(fetched, pinned, owner, backedByShared);
                    }
                    // Entry already exists from an earlier injection point — refresh with latest data.
                    existing.update(fetched);
                    existing.effectiveOwner = owner;
                    existing.backedByShared = backedByShared;
                    return existing;
                });

                evictIfNeeded();

                return new DefaultArtifactHandle(
                        this, keyOwner, descriptor.artifactName(), descriptor.alias(), descriptor.cacheable());
            }
        }

        return handleMissing(descriptor);
    }

    /**
     * Return bytes for a <em>cacheable</em> handle. Returns the currently cached bytes without
     * re-downloading; the cache is kept current via notifications and the background poll.
     */
    byte[] getBytes(String owner, String artifactName, String alias) {
        CacheEntry entry = cache.get(new CacheKey(owner, artifactName, alias));
        return entry != null ? entry.getArtifact().bytes() : new byte[0];
    }

    /**
     * Return bytes for a <em>non-cacheable</em> handle. Always downloads fresh bytes and
     * updates the shared cache entry so cacheable handles also see the latest data.
     */
    byte[] getFreshBytes(String owner, String artifactName, String alias) {
        CacheEntry entry = cache.get(new CacheKey(owner, artifactName, alias));
        // Use effectiveOwner so that PLUGIN_FIRST entries backed by shared (or promoted) still
        // fetch from the correct namespace rather than reverting to the cache key owner.
        String downloadOwner = entry != null ? entry.effectiveOwner : owner;
        FetchedArtifact fresh = client.download(downloadOwner, artifactName, alias);
        if (fresh == null) {
            if (entry != null) {
                log.warn("Artifact {}/{}/{} is no longer available in the registry — returning cached bytes (v{})",
                        downloadOwner, artifactName, alias, entry.getArtifact().manifest().version());
                return entry.getArtifact().bytes();
            }
            return new byte[0];
        }
        if (entry != null) {
            entry.update(fresh);
        }
        return fresh.bytes();
    }

    /**
     * Return the current cached {@link FetchedArtifact} for metadata access.
     * May be {@code null} if the entry has been evicted (should not happen in normal use).
     */
    FetchedArtifact getCurrent(String owner, String artifactName, String alias) {
        CacheEntry entry = cache.get(new CacheKey(owner, artifactName, alias));
        return entry != null ? entry.getArtifact() : null;
    }

    /**
     * Force a re-download and update the shared cache entry. Called by
     * {@link DefaultArtifactHandle#refresh()}.
     */
    void refresh(String owner, String artifactName, String alias) {
        CacheEntry entry = cache.get(new CacheKey(owner, artifactName, alias));
        if (entry == null) {
            return;
        }
        FetchedArtifact fresh = client.download(entry.effectiveOwner, artifactName, alias);
        if (fresh != null) {
            entry.update(fresh);
        } else {
            log.warn("Artifact {}/{}/{} is no longer available in the registry — refresh had no effect (retaining v{})",
                    entry.effectiveOwner, artifactName, alias, entry.getArtifact().manifest().version());
        }
    }

    /**
     * Re-download and update the cache entry only if it is already present.
     * Called by the notification listener on version-change events.
     *
     * <p><b>Shared-owner path</b> ({@code owner = "shared"}): updates the {@code (shared, …)}
     * entry if present, then calls {@link #propagateSharedUpdate} to push the same bytes to any
     * {@link ArtifactScope#PLUGIN_FIRST} entries that are currently {@code backedByShared}.
     *
     * <p><b>Plugin-owner path</b> ({@code owner ≠ "shared"}): downloads from the plugin namespace
     * and updates the entry. If the entry was {@code backedByShared}, this constitutes a promotion
     * and is logged accordingly; {@code effectiveOwner} and {@code backedByShared} are updated
     * unconditionally (a no-op for entries that were already plugin-backed). If the artifact is
     * gone by the time the download is attempted, a warning is logged and cached bytes are retained.
     */
    void refreshIfCached(String owner, String artifactName, String alias) {
        CacheEntry entry = cache.get(new CacheKey(owner, artifactName, alias));
        FetchedArtifact fresh = null;
        if (entry != null) {
            fresh = client.download(owner, artifactName, alias);
            if (fresh != null) {
                if (entry.backedByShared) {
                    log.info("Artifact '{}/{}' (alias '{}'): PLUGIN_FIRST entry promoted from shared (v{}) to plugin-specific (v{})",
                            owner, artifactName, alias, entry.getArtifact().manifest().version(), fresh.manifest().version());
                }
                entry.notifyUpdate(fresh);
                entry.effectiveOwner = owner;
                // this event was either plugin specific or we are acting on a shared entry, either way this needs to be false now
                entry.backedByShared = false;
            } else {
                log.warn("Artifact {}/{}/{} is no longer available in the registry — retaining cached version {}",
                        owner, artifactName, alias, entry.getArtifact().manifest().version());
            }
        }
        if (ArtifactRegistryConstants.SHARED_OWNER.equals(owner)) {
            propagateSharedUpdate(artifactName, alias, fresh);
        }
    }

    /**
     * Propagate a shared-namespace update to all {@link ArtifactScope#PLUGIN_FIRST} entries that
     * are currently {@code backedByShared} and match the given {@code (artifactName, alias)}.
     *
     * <p>If {@code alreadyFetched} is non-null (because the shared entry was already refreshed
     * by the caller), those bytes are reused without a second download. Otherwise, the artifact is
     * downloaded from the shared namespace once and reused for every matching entry. If the shared
     * namespace no longer has the artifact, a warning is logged and cached bytes are retained.
     *
     * <p>Callbacks are fired on the plugin-owner key of each updated entry so that handles
     * registered via {@link DefaultArtifactHandle#onUpdate} are notified.
     */
    private void propagateSharedUpdate(String artifactName, String alias, FetchedArtifact alreadyFetched) {
        FetchedArtifact fresh = alreadyFetched;
        for (Map.Entry<CacheKey, CacheEntry> e : cache.entrySet()) {
            CacheKey key = e.getKey();
            CacheEntry entry = e.getValue();
            if (entry.backedByShared
                    && key.artifactName().equals(artifactName)
                    && key.alias().equals(alias)) {
                if (fresh == null) {
                    fresh = client.download(ArtifactRegistryConstants.SHARED_OWNER, artifactName, alias);
                    if (fresh == null) {
                        log.warn("Shared artifact {}/{} no longer available — backedByShared entries retain cached bytes",
                                artifactName, alias);
                        return;
                    }
                }
                String prev = entry.getArtifact().manifest().version();
                entry.notifyUpdate(fresh);
                if (!fresh.manifest().version().equals(prev)) {
                    VersionChangedEvent event = new VersionChangedEvent("shared", artifactName, alias,
                            prev, fresh.manifest().version(), Instant.now());
                    invokeCallbacks(key.owner(), artifactName, alias, event);
                }
            }
        }
    }

    /**
     * Polls the registry for each unique owner that has non-pinned cached entries and
     * re-fetches any entry whose alias currently points to a different version than what
     * is in the cache. Fires {@code onUpdate} callbacks after refreshing, just as the
     * notification path does.
     *
     * <p>This method is package-private so it can be called directly from tests without
     * relying on the background scheduler.
     */
    void pollForChanges() {
        if (cache.isEmpty()) {
            return;
        }

        Map<String, List<CacheKey>> byOwner = groupEntriesByOwner();
        for (Map.Entry<String, List<CacheKey>> ownerEntry : byOwner.entrySet()) {
            String owner = ownerEntry.getKey();
            try {
                pollForChanges(owner, ownerEntry.getValue());
            } catch (Exception e) {
                log.warn("Artifact cache poll failed for owner '{}': {}", owner, e.getMessage());
            }
        }
    }

    private void pollForChanges(String owner, List<CacheKey> ownerEntries) {
        Map<String, Map<String, String>> aliasLookup = buildAliasMap(owner);
        if (!aliasLookup.isEmpty()) {
            for (CacheKey key : ownerEntries) {
                String remoteVersion = aliasLookup.getOrDefault(key.artifactName(), Collections.emptyMap()).get(key.alias());
                CacheEntry entry = cache.get(key);
                if (remoteVersion == null && entry != null) {
                    // Artifact is absent from the registry listing but we have a cached version.
                    // Warn and continue serving the cached bytes — operator action is required.
                    log.warn("Artifact {}/{}/{} is no longer in the registry — continuing with cached version {} " +
                                    "(restart the plugin or upload a replacement to restore normal operation)",
                            owner, key.artifactName(), key.alias(), entry.getArtifact().manifest().version());
                } else if (versionHasDiverged(remoteVersion, entry)) {
                    String cachedVersion = entry.getArtifact().manifest().version();
                    log.debug("Poll detected version change for {}/{}/{}: {} → {}",
                            owner, key.artifactName(), key.alias(), cachedVersion, remoteVersion);

                    FetchedArtifact fresh = client.download(owner, key.artifactName(), key.alias());
                    if (fresh != null) {
                        entry.notifyUpdate(fresh);
                        // Use the effective (download) owner for the event prefix; use the cache
                        // key owner for callback lookup since handles register under the key owner.
                        String eventPrefix = ArtifactRegistryConstants.SHARED_OWNER.equals(owner)
                                ? "shared" : "plugins/" + owner;
                        VersionChangedEvent event = new VersionChangedEvent(
                                eventPrefix, key.artifactName(), key.alias(),
                                cachedVersion, remoteVersion, Instant.now());
                        invokeCallbacks(key.owner(), key.artifactName(), key.alias(), event);
                    }
                }
            }
        }
    }

    // Group non-pinned entries by effective owner (which may differ from the cache key owner
    // for PLUGIN_FIRST entries backed by shared or that have been promoted).
    private Map<String, List<CacheKey>> groupEntriesByOwner() {
        Map<String, List<CacheKey>> byOwner = new LinkedHashMap<>();
        for (Map.Entry<CacheKey, CacheEntry> e : cache.entrySet()) {
            if (!e.getValue().isPinned()) {
                byOwner.computeIfAbsent(e.getValue().effectiveOwner, o -> new ArrayList<>())
                        .add(e.getKey());
            }
        }
        return byOwner;
    }

    // build a map holding a lookup map of artifactName to a map of alias to version
    private Map<String, Map<String, String>> buildAliasMap(String owner) {
        // Holds a lookup map of artifactName to a map of alias to version
        Map<String, Map<String, String>> aliasLookup = new HashMap<>();
        ArtifactRegistryClient.ArtifactListResponse response = client.listArtifacts(owner);
        if (response == null || response.artifacts() == null) {
            return aliasLookup;
        }

        for (ArtifactRegistryClient.ArtifactSummaryEntry summary : response.artifacts()) {
            if (summary.aliases() != null) {
                aliasLookup.put(summary.artifactName(), summary.aliases());
            }
        }
        return aliasLookup;
    }

    private boolean versionHasDiverged(String remoteVersion, CacheEntry entry) {
        //  if the remote version is not found or the cache no longer holds this entry there is nothing to do
        if (remoteVersion == null || entry == null) {
            return false;
        }
        // Compare against lastNotifiedVersion rather than the cached artifact version so that
        // non-cacheable handles (whose getFreshBytes calls continuously update the artifact)
        // still trigger callbacks via the poll safety net when they miss a push notification.
        return !remoteVersion.equals(entry.lastNotifiedVersion);
    }

    /**
     * Register a callback keyed by {@code (owner, artifactName, alias)}.
     * Callbacks fire only when the event's namespace matches the owner — preventing
     * cross-namespace spurious firings.
     */
    void registerCallback(String owner, String artifactName, String alias, Consumer<VersionChangedEvent> callback) {
        callbacks.computeIfAbsent(new CacheKey(owner, artifactName, alias),
                k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * Fire all registered callbacks with a synthetic event for each cached entry.
     * Called after all beans are fully initialized so that {@code onUpdate} callbacks
     * registered in {@code @PostConstruct} methods are included. Each callback receives
     * a {@link VersionChangedEvent} with {@code previousVersion = null}, mirroring the
     * Go/C++ pattern where initial resolution is treated as a version change.
     */
    void fireStartupCallbacks() {
        for (Map.Entry<CacheKey, CopyOnWriteArrayList<Consumer<VersionChangedEvent>>> entry : callbacks.entrySet()) {
            CacheKey key = entry.getKey();
            CacheEntry cached = cache.get(key);
            String currentVersion = cached != null ? cached.getArtifact().manifest().version() : "";
            String prefix = ArtifactRegistryConstants.SHARED_OWNER.equals(key.owner())
                    ? "shared" : "plugins/" + key.owner();
            VersionChangedEvent event = new VersionChangedEvent(
                    prefix, key.artifactName(), key.alias(), null, currentVersion, Instant.now());
            invokeCallbacks(key.owner(), key.artifactName(), key.alias(), event);
        }
    }

    /**
     * Invoke all callbacks registered for {@code (owner, artifactName, alias)}, passing the event.
     * Each callback runs on the caller's thread; failures are logged and do not
     * prevent subsequent callbacks from running.
     */
    void invokeCallbacks(String owner, String artifactName, String alias, VersionChangedEvent event) {
        CopyOnWriteArrayList<Consumer<VersionChangedEvent>> cbs =
                callbacks.get(new CacheKey(owner, artifactName, alias));
        if (cbs == null) {
            return;
        }
        for (Consumer<VersionChangedEvent> cb : cbs) {
            try {
                cb.accept(event);
            } catch (Exception e) {
                log.warn("Artifact update callback failed for '{}/{}/{}': {}",
                        owner, artifactName, alias, e.getMessage(), e);
            }
        }
    }

    /**
     * Evict the least-recently-fetched non-pinned cache entries until the cache is within the
     * configured {@code maxEntries} and {@code maxBytes} limits. Pinned entries (concrete-version
     * references) are skipped; if the limits cannot be satisfied without touching pinned entries,
     * a warning is logged.
     */
    private void evictIfNeeded() {
        int maxE = props.getMaxEntries();
        long maxB = props.getMaxBytes();
        if (maxE <= 0 && maxB <= 0) {
            return;
        }

        // Eviction candidates: non-pinned entries sorted oldest-first
        List<Map.Entry<CacheKey, CacheEntry>> candidates = new ArrayList<>(cache.entrySet());
        candidates.removeIf(e -> e.getValue().isPinned());
        candidates.sort(Comparator.comparing(e -> e.getValue().lastFetchedAt()));

        int totalEntries = cache.size();
        long totalBytes = cache.values().stream().mapToLong(CacheEntry::byteCount).sum();

        for (Map.Entry<CacheKey, CacheEntry> candidate : candidates) {
            boolean overEntries = maxE > 0 && totalEntries > maxE;
            boolean overBytes = maxB > 0 && totalBytes > maxB;
            if (!overEntries && !overBytes) {
                break;
            }
            long entryBytes = candidate.getValue().byteCount();
            if (cache.remove(candidate.getKey(), candidate.getValue())) {
                totalEntries--;
                totalBytes -= entryBytes;
                log.debug("Evicted artifact cache entry {}/{}/{}: freed {} bytes",
                        candidate.getKey().owner(), candidate.getKey().artifactName(),
                        candidate.getKey().alias(), entryBytes);
            }
        }

        boolean stillOverEntries = maxE > 0 && cache.size() > maxE;
        long remaining = cache.values().stream().mapToLong(CacheEntry::byteCount).sum();
        boolean stillOverBytes = maxB > 0 && remaining > maxB;
        if (stillOverEntries || stillOverBytes) {
            log.warn("Artifact cache still exceeds limits after evicting all non-pinned entries " +
                    "(entries: {}/{}, bytes: {}/{})", cache.size(), maxE, remaining, maxB);
        }
    }

    /**
     * For shared-namespace downloads only: return bytes already in the cache rather than
     * downloading again. Checks the {@code ("shared", …)} entry first, then any
     * {@link ArtifactScope#PLUGIN_FIRST} entry that is currently {@code backedByShared}.
     * For all other owners the download is always performed fresh.
     */
    private FetchedArtifact reuseOrDownload(String owner, String artifactName, String alias) {
        if (ArtifactRegistryConstants.SHARED_OWNER.equals(owner)) {
            // Case 1: SHARED_ONLY already downloaded — ("shared", …) entry exists.
            CacheEntry sharedEntry = cache.get(new CacheKey(owner, artifactName, alias));
            if (sharedEntry != null) {
                return sharedEntry.getArtifact();
            }
            // Case 2: PLUGIN_FIRST fell back first — its entry holds shared bytes.
            for (Map.Entry<CacheKey, CacheEntry> e : cache.entrySet()) {
                if (e.getValue().backedByShared
                        && e.getKey().artifactName().equals(artifactName)
                        && e.getKey().alias().equals(alias)) {
                    return e.getValue().getArtifact();
                }
            }
        }
        return client.download(owner, artifactName, alias);
    }

    private List<String> ownersFor(ArtifactDescriptor descriptor) {
        String pluginOwner = descriptor.pluginName();
        return switch (descriptor.scope()) {
            case PLUGIN_ONLY -> List.of(pluginOwner);
            case SHARED_ONLY -> List.of(ArtifactRegistryConstants.SHARED_OWNER);
            case PLUGIN_FIRST -> List.of(pluginOwner, ArtifactRegistryConstants.SHARED_OWNER);
        };
    }

    private ArtifactHandle handleMissing(ArtifactDescriptor descriptor) {
        if (!descriptor.seedOnMissing()) {
            throw new IllegalStateException("Artifact not found: " + descriptor.artifactName());
        }

        byte[] bytes;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(descriptor.defaultResource())) {
            if (in == null) {
                throw new IllegalStateException("Default resource not found: " + descriptor.defaultResource());
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new WrappedIOException(e);
        }

        ArtifactManifest manifest = client.seedIfMissing(
                descriptor.pluginName(), descriptor.artifactName(), descriptor.alias(), bytes);
        if (manifest == null) {
            throw new IllegalStateException("Failed to seed default artifact: " + descriptor.artifactName());
        }

        String sourcePrefix = "plugins/" + descriptor.pluginName();
        FetchedArtifact fetched = new FetchedArtifact(manifest, sourcePrefix, bytes);
        CacheKey key = new CacheKey(descriptor.pluginName(), descriptor.artifactName(), descriptor.alias());
        cache.put(key, new CacheEntry(fetched, false, descriptor.pluginName(), false));

        evictIfNeeded();

        return new DefaultArtifactHandle(
                this, descriptor.pluginName(), descriptor.artifactName(), descriptor.alias(), descriptor.cacheable());
    }

    private static class WrappedIOException extends RuntimeException {
        public WrappedIOException(IOException exception) {
            super(exception);
        }
    }
}
