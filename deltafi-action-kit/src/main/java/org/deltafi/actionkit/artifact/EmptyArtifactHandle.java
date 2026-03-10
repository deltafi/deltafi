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
import java.util.function.Consumer;

/** A no-op {@link ArtifactHandle} returned when an artifact could not be resolved. */
class EmptyArtifactHandle implements ArtifactHandle {

    private final String artifactName;

    EmptyArtifactHandle(String artifactName) {
        this.artifactName = artifactName;
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public InputStream getInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ArtifactManifest getManifest() {
        return new ArtifactManifest(null, null, null, null, null);
    }

    @Override
    public Map<String, String> getLabels() {
        return null;
    }

    @Override
    public String getSha256() {
        return null;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public String getSourcePrefix() {
        return null;
    }

    @Override
    public boolean isShared() {
        return false;
    }

    @Override
    public void refresh() {
        // no-op
    }

    @Override
    public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
        return this;
    }
}
