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

import org.deltafi.common.artifact.VersionChangedEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A lightweight {@link ArtifactHandle} for programmatic artifact access via {@link ArtifactRegistryService}.
 *
 * <p>Holds the fetched artifact in memory without going through the shared {@link ArtifactCacheManager},
 * keeping {@link ArtifactRegistryService} independent of the cache infrastructure.
 *
 * <p>Callbacks registered via {@link #onUpdate} fire only on explicit {@link #refresh()} calls —
 * not from the Valkey pub/sub system. For reactive cache invalidation use {@code @PluginArtifact}
 * injection instead.
 */
class DirectArtifactHandle implements ArtifactHandle {

    private final ArtifactRegistryClient client;
    private final String owner;
    private final String artifactName;
    private final String alias;
    private volatile FetchedArtifact fetched;
    private final List<Consumer<VersionChangedEvent>> callbacks = new CopyOnWriteArrayList<>();

    DirectArtifactHandle(ArtifactRegistryClient client, String owner,
                         String artifactName, String alias, FetchedArtifact fetched) {
        this.client = client;
        this.owner = owner;
        this.artifactName = artifactName;
        this.alias = alias;
        this.fetched = fetched;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    // ── Bytes ─────────────────────────────────────────────────────────────────

    @Override
    public byte[] getBytes() {
        return fetched.bytes();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(fetched.bytes());
    }

    // ── Manifest / metadata ───────────────────────────────────────────────────

    @Override
    public ArtifactManifest getManifest() {
        return fetched.manifest();
    }

    @Override
    public String getVersion() {
        return fetched.manifest().version();
    }

    @Override
    public String getDescription() {
        return fetched.manifest().description();
    }

    @Override
    public String getContentType() {
        return fetched.manifest().contentType();
    }

    @Override
    public Map<String, String> getLabels() {
        return fetched.manifest().labels();
    }

    @Override
    public String getSha256() {
        return fetched.manifest().sha256();
    }

    @Override
    public String getSourcePrefix() {
        return fetched.sourcePrefix();
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isDefault() {
        return fetched.manifest().isDefault();
    }

    @Override
    public boolean isShared() {
        return "shared".equals(fetched.sourcePrefix());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        String previousVersion = fetched.manifest().version();
        FetchedArtifact updated = client.download(owner, artifactName, alias);
        if (updated == null) {
            return;
        }
        fetched = updated;
        if (!updated.manifest().version().equals(previousVersion)) {
            VersionChangedEvent event = new VersionChangedEvent(
                    fetched.sourcePrefix(), artifactName, alias,
                    previousVersion, updated.manifest().version(), java.time.Instant.now());
            for (Consumer<VersionChangedEvent> callback : callbacks) {
                callback.accept(event);
            }
        }
    }

    // ── Update callbacks ──────────────────────────────────────────────────────

    @Override
    public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
        callbacks.add(callback);
        return this;
    }
}
