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
import java.util.Map;
import java.util.function.Consumer;

/**
 * An {@link ArtifactHandle} backed by the centralized {@link ArtifactCacheManager}.
 *
 * <p>No bytes are held locally — all reads delegate to the manager so that multiple injection
 * points sharing the same artifact consume only one in-memory copy.
 *
 * <p>Non-cacheable handles ({@code cacheable = false}, the default) always request a fresh
 * download, which also updates the shared cache for any co-located cacheable handles.
 * Cacheable handles ({@code cacheable = true}) ask the manager for potentially-cached bytes,
 * which are re-fetched only when a version-change notification or the background poll detects drift.
 */
class DefaultArtifactHandle implements ArtifactHandle {

    private final ArtifactCacheManager manager;
    private final String owner;
    private final String artifactName;
    private final String alias;
    /** {@code true} when the injection point explicitly opted in to in-memory caching. */
    private final boolean cacheable;

    DefaultArtifactHandle(ArtifactCacheManager manager, String owner, String artifactName,
                          String alias, boolean cacheable) {
        this.manager = manager;
        this.owner = owner;
        this.artifactName = artifactName;
        this.alias = alias;
        this.cacheable = cacheable;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    // ── Bytes ─────────────────────────────────────────────────────────────────

    @Override
    public byte[] getBytes() {
        return cacheable
                ? manager.getBytes(owner, artifactName, alias)
                : manager.getFreshBytes(owner, artifactName, alias);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(getBytes());
    }

    // ── Manifest / metadata ───────────────────────────────────────────────────

    @Override
    public ArtifactManifest getManifest() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest() : new ArtifactManifest(null, null, null, null, null);
    }

    @Override
    public String getVersion() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest().version() : null;
    }

    @Override
    public String getDescription() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest().description() : null;
    }

    @Override
    public String getContentType() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest().contentType() : null;
    }

    @Override
    public Map<String, String> getLabels() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest().labels() : null;
    }

    @Override
    public String getSha256() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.manifest().sha256() : null;
    }

    @Override
    public String getSourcePrefix() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null ? current.sourcePrefix() : null;
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isDefault() {
        FetchedArtifact current = manager.getCurrent(owner, artifactName, alias);
        return current != null && current.manifest().isDefault();
    }

    @Override
    public boolean isShared() {
        return "shared".equals(getSourcePrefix());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        manager.refresh(owner, artifactName, alias);
    }

    // ── Update callbacks ──────────────────────────────────────────────────────

    @Override
    public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
        manager.registerCallback(owner, artifactName, alias, callback);
        return this;
    }
}
