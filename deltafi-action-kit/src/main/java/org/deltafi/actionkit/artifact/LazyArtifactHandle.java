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

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An {@link ArtifactHandle} that defers artifact loading until first access.
 *
 * <p>Callbacks registered via {@link #onUpdate} before the handle is resolved are buffered
 * and forwarded to the resolved delegate on first access. The delegate ({@link DefaultArtifactHandle})
 * registers them against the correct owner namespace — so callbacks only fire for the namespace
 * this handle actually resolved from.
 */
class LazyArtifactHandle implements ArtifactHandle {

    private final String artifactName;
    private final Supplier<ArtifactHandle> loader;
    /** Callbacks registered before the delegate is resolved; cleared after forwarding. */
    private final CopyOnWriteArrayList<Consumer<VersionChangedEvent>> pendingCallbacks =
            new CopyOnWriteArrayList<>();
    private volatile ArtifactHandle delegate;

    LazyArtifactHandle(String artifactName, Supplier<ArtifactHandle> loader) {
        this.artifactName = artifactName;
        this.loader = loader;
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    private ArtifactHandle delegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    ArtifactHandle resolved = loader.get();
                    for (Consumer<VersionChangedEvent> cb : pendingCallbacks) {
                        resolved.onUpdate(cb);
                    }
                    pendingCallbacks.clear();
                    delegate = resolved;
                }
            }
        }
        return delegate;
    }

    @Override
    public String getVersion() {
        return delegate().getVersion();
    }

    @Override
    public String getDescription() {
        return delegate().getDescription();
    }

    @Override
    public String getContentType() {
        return delegate().getContentType();
    }

    @Override
    public byte[] getBytes() {
        return delegate().getBytes();
    }

    @Override
    public InputStream getInputStream() {
        return delegate().getInputStream();
    }

    @Override
    public ArtifactManifest getManifest() {
        return delegate().getManifest();
    }

    @Override
    public Map<String, String> getLabels() {
        return delegate().getLabels();
    }

    @Override
    public String getSha256() {
        return delegate().getSha256();
    }

    @Override
    public boolean isReady() {
        return delegate != null && delegate.isReady();
    }

    @Override
    public boolean isDefault() {
        return delegate().isDefault();
    }

    @Override
    public String getSourcePrefix() {
        return delegate().getSourcePrefix();
    }

    @Override
    public boolean isShared() {
        return delegate().isShared();
    }

    @Override
    public void refresh() {
        if (delegate == null) {
            delegate();
        } else {
            delegate.refresh();
        }
    }

    @Override
    public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
        if (delegate != null) {
            delegate.onUpdate(callback);
        } else {
            synchronized (this) {
                if (delegate != null) {
                    delegate.onUpdate(callback);
                } else {
                    pendingCallbacks.add(callback);
                }
            }
        }
        return this;
    }
}
