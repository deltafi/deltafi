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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A handle to an artifact stored in the DeltaFi artifact registry.
 *
 * <p>Instances are obtained by annotating a field with {@link PluginArtifact}:
 * <pre>{@code
 * @PluginArtifact(artifactName = "my-model")
 * private ArtifactHandle model;
 * }</pre>
 *
 * <p>Handles are either <em>eager</em> (bytes pre-fetched at startup) or <em>lazy</em>
 * (fetched on first access), controlled by {@link PluginArtifact#preload()}.
 * Non-cacheable handles (the default) re-fetch from the registry on each call to
 * {@link #getBytes()}; cacheable handles retain bytes in memory between calls.
 */
public interface ArtifactHandle {

    /**
     * Return the artifact identifier (registry name) of this handle, e.g. {@code "routing-rules.json"}.
     *
     * @return the artifact name as registered in the registry; never {@code null}
     */
    String getArtifactName();

    /**
     * Return the resolved version string of this artifact, e.g. {@code "20240101-120000"}.
     *
     * @return the concrete version that was resolved, or {@code null} for empty handles
     */
    String getVersion();

    /**
     * Return the human-readable description of this artifact, or {@code null} if not set.
     */
    String getDescription();

    /**
     * Return the MIME type of the artifact content, e.g. {@code "application/octet-stream"}.
     */
    String getContentType();

    /**
     * Return the raw artifact bytes.
     *
     * <p>For non-cacheable handles (the default), each call triggers a new network fetch.
     * For cacheable handles, bytes are retained in memory and returned on every call.
     *
     * @return artifact content as a byte array; never {@code null}
     */
    byte[] getBytes();

    /**
     * Open a fresh {@link InputStream} over the artifact bytes.
     *
     * <p>The caller is responsible for closing the returned stream.
     * Each call returns an independent stream positioned at the start.
     *
     * @return a new input stream over the artifact content
     */
    InputStream getInputStream();

    /**
     * Return the full manifest metadata for this artifact version.
     *
     * @return the manifest; never {@code null} (returns an empty manifest for unresolved handles)
     */
    ArtifactManifest getManifest();

    /**
     * Return the labels map associated with this artifact, or {@code null} if none were set.
     */
    Map<String, String> getLabels();

    /**
     * Return the SHA-256 digest of the artifact content, or {@code null} for empty handles.
     */
    String getSha256();

    /**
     * Return {@code true} if this handle has successfully resolved an artifact.
     * Returns {@code false} for empty/unresolved handles.
     */
    boolean isReady();

    /**
     * Return {@code true} if this artifact was published with the {@code default=true} label,
     * indicating it was seeded as a plugin default via {@link PluginArtifact#defaultResource()}.
     */
    boolean isDefault();

    /**
     * Return the storage source prefix, either {@code "shared"} for shared artifacts or
     * {@code "plugins/{owner}"} for plugin-scoped artifacts.
     */
    String getSourcePrefix();

    /**
     * Return {@code true} if this artifact was loaded from the shared namespace
     * rather than the plugin's own namespace.
     *
     * @return {@code true} for shared artifacts, {@code false} for plugin-scoped artifacts
     */
    boolean isShared();

    /**
     * Re-fetch this artifact from the registry and update the manifest.
     * For cacheable handles, the cached bytes are also refreshed.
     */
    void refresh();

    /**
     * Register a callback to be invoked whenever this artifact's version changes.
     *
     * <p>The callback receives the {@link VersionChangedEvent} describing the change
     * (previous version, new version, alias, etc.). It fires after the cache has been
     * refreshed, so {@link #getBytes()} inside the callback returns the new version's bytes.
     * Callbacks are invoked on the listener thread — long-running work should be
     * dispatched to an executor.
     *
     * @param callback the consumer to run on version change; must not be {@code null}
     * @return {@code this}, for fluent chaining
     */
    ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback);

    /**
     * Convenience overload for callers that do not need the change event.
     *
     * @param callback the action to run on version change; must not be {@code null}
     * @return {@code this}, for fluent chaining
     */
    default ArtifactHandle onUpdate(Runnable callback) {
        return onUpdate(event -> callback.run());
    }

    /**
     * Decode the artifact content as a {@link String} using the given charset.
     *
     * <p>Equivalent to {@code new String(getBytes(), charset)}.
     *
     * @param charset the charset to use for decoding; must not be {@code null}
     * @return artifact content decoded as a string
     */
    default String getString(Charset charset) {
        return new String(getBytes(), charset);
    }

    /**
     * Decode the artifact content as a UTF-8 {@link String}.
     *
     * <p>Equivalent to {@code getString(StandardCharsets.UTF_8)}. Use this for text-based
     * artifacts such as JSON, YAML, CSV, or plain-text configuration files.
     *
     * @return artifact content decoded as a UTF-8 string
     */
    default String getString() {
        return getString(StandardCharsets.UTF_8);
    }
}
