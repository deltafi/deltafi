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

import org.deltafi.common.artifact.ArtifactRegistryConstants;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Artifact registry API for plugins: publish new versions and fetch existing ones.
 * The plugin owner ({@code group.artifactId}) is pre-bound and does not need to be supplied.
 *
 * <p>Deletion and alias management are administrative operations handled outside the plugin
 * lifecycle — use the TUI or REST API directly for those.
 *
 * <pre>{@code
 * @Autowired
 * private ArtifactRegistry artifactRegistry;
 *
 * artifactRegistry.publishArtifact("my-model",
 *         new ArtifactManifest(null, "Trained model v2", "application/octet-stream", null, null),
 *         Files.newInputStream(modelPath));
 * }</pre>
 */
@SuppressWarnings("unused")
public class ArtifactRegistryService {

    private final ArtifactRegistryClient client;

    // Package-private: created only by ArtifactConfiguration
    ArtifactRegistryService(ArtifactRegistryClient client) {
        this.client = client;
    }

    /**
     * Publish a new artifact version scoped to this plugin. The {@code active} alias is updated.
     *
     * @param artifactName artifact identifier (unique within the plugin namespace)
     * @param manifest     artifact metadata; {@code manifest.version()} may be null to let the
     *                     server generate a timestamp-based version
     * @param data         artifact byte stream — caller is responsible for closing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data) throws IOException {
        return client.publishArtifact(artifactName, manifest, data, true);
    }

    /**
     * Publish a new artifact version scoped to this plugin.
     *
     * @param artifactName artifact identifier (unique within the plugin namespace)
     * @param manifest     artifact metadata; {@code manifest.version()} may be null to let the
     *                     server generate a timestamp-based version
     * @param data         artifact byte stream — caller is responsible for closing
     * @param makeActive   whether to update the {@code active} alias after publishing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive) throws IOException {
        return client.publishArtifact(artifactName, manifest, data, makeActive);
    }

    /**
     * Publish a new artifact version to the shared namespace (visible to all plugins).
     * The {@code active} alias is updated.
     *
     * @param artifactName artifact identifier (unique within the shared namespace)
     * @param manifest     artifact metadata; {@code manifest.version()} may be null to let the
     *                     server generate a timestamp-based version
     * @param data         artifact byte stream — caller is responsible for closing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishSharedArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data) throws IOException {
        return client.publishSharedArtifact(artifactName, manifest, data, true);
    }

    /**
     * Publish a new artifact version to the shared namespace (visible to all plugins).
     *
     * @param artifactName artifact identifier (unique within the shared namespace)
     * @param manifest     artifact metadata; {@code manifest.version()} may be null to let the
     *                     server generate a timestamp-based version
     * @param data         artifact byte stream — caller is responsible for closing
     * @param makeActive   whether to update the {@code active} alias after publishing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishSharedArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive) throws IOException {
        return client.publishSharedArtifact(artifactName, manifest, data, makeActive);
    }

    /**
     * Fetch an active plugin-scoped artifact and return an {@link ArtifactHandle}.
     *
     * <p>The handle is resolved immediately — the artifact bytes are fetched during this call.
     *
     * @param artifactName artifact identifier
     * @return a ready {@link ArtifactHandle}
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactHandle getArtifact(String artifactName) throws IOException {
        return getArtifact(artifactName, ArtifactRegistryConstants.ACTIVE_ALIAS);
    }

    /**
     * Fetch a plugin-scoped artifact by alias and return an {@link ArtifactHandle}.
     *
     * <p>The handle is resolved immediately — the artifact bytes are fetched during this call.
     * Use {@code "active"} as the alias to resolve the current active version.
     *
     * @param artifactName artifact identifier
     * @param alias        alias or concrete version to fetch
     * @return a ready {@link ArtifactHandle}
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactHandle getArtifact(String artifactName, String alias) throws IOException {
        return fetchHandle(client.getPluginName(), artifactName, alias);
    }

    /**
     * Fetch the active version of a shared artifact and return an {@link ArtifactHandle}.
     *
     * <p>The handle is resolved immediately — the artifact bytes are fetched during this call.
     *
     * @param artifactName artifact identifier
     * @return a ready {@link ArtifactHandle}
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactHandle getSharedArtifact(String artifactName) throws IOException {
        return getSharedArtifact(artifactName, ArtifactRegistryConstants.ACTIVE_ALIAS);
    }

    /**
     * Fetch a shared artifact by alias and return an {@link ArtifactHandle}.
     *
     * <p>The handle is resolved immediately — the artifact bytes are fetched during this call.
     * Use {@code "active"} as the alias to resolve the current active version.
     *
     * @param artifactName artifact identifier
     * @param alias        alias or concrete version to fetch
     * @return a ready {@link ArtifactHandle}
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactHandle getSharedArtifact(String artifactName, String alias) throws IOException {
        return fetchHandle(ArtifactRegistryConstants.SHARED_OWNER, artifactName, alias);
    }

    // ── Streaming access ──────────────────────────────────────────────────────

    /**
     * Open a streaming connection to the active version of a plugin-scoped artifact without
     * buffering the bytes in memory.
     *
     * <p>Use this instead of {@link #getArtifact} when the artifact is too large to hold in the
     * JVM heap — for example, to load a large ML model directly into a framework that accepts an
     * {@link InputStream}, or to pipe content to disk or an external encoder.
     *
     * <p>The returned {@link ArtifactStream} <strong>must</strong> be closed when done, typically
     * via try-with-resources. The underlying HTTP connection stays open until the stream is closed.
     *
     * <p>Streaming bypasses the shared cache entirely. Each call opens a new connection to the
     * registry. For small, frequently-read artifacts prefer {@link #getArtifact} instead.
     *
     * @param artifactName artifact identifier
     * @return a stream holding the manifest and the open body stream
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactStream streamArtifact(String artifactName) throws IOException {
        return streamArtifact(artifactName, ArtifactRegistryConstants.ACTIVE_ALIAS);
    }

    /**
     * Open a streaming connection to a plugin-scoped artifact by alias without buffering the
     * bytes in memory.
     *
     * <p>See {@link #streamArtifact(String)} for full usage notes.
     *
     * @param artifactName artifact identifier
     * @param alias        alias or concrete version to stream (e.g. {@code "active"})
     * @return a stream holding the manifest and the open body stream
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactStream streamArtifact(String artifactName, String alias) throws IOException {
        return fetchStream(client.getPluginName(), artifactName, alias);
    }

    /**
     * Open a streaming connection to the active version of a shared artifact without buffering
     * the bytes in memory.
     *
     * <p>See {@link #streamArtifact(String)} for full usage notes.
     *
     * @param artifactName artifact identifier
     * @return a stream holding the manifest and the open body stream
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactStream streamSharedArtifact(String artifactName) throws IOException {
        return streamSharedArtifact(artifactName, ArtifactRegistryConstants.ACTIVE_ALIAS);
    }

    /**
     * Open a streaming connection to a shared artifact by alias without buffering the bytes
     * in memory.
     *
     * <p>See {@link #streamArtifact(String)} for full usage notes.
     *
     * @param artifactName artifact identifier
     * @param alias        alias or concrete version to stream (e.g. {@code "active"})
     * @return a stream holding the manifest and the open body stream
     * @throws ArtifactNotFoundException if the artifact or alias does not exist (HTTP 404)
     * @throws IOException               if the request fails for any other reason
     */
    public ArtifactStream streamSharedArtifact(String artifactName, String alias) throws IOException {
        return fetchStream(ArtifactRegistryConstants.SHARED_OWNER, artifactName, alias);
    }

    private ArtifactStream fetchStream(String owner, String artifactName, String alias) throws IOException {
        ArtifactStream stream = client.stream(owner, artifactName, alias);
        if (stream == null) {
            throw new ArtifactNotFoundException(owner, artifactName, alias);
        }
        return stream;
    }

    /**
     * A streaming artifact response that holds the manifest metadata and the open HTTP body
     * stream. The stream is backed by a live HTTP connection and <strong>must</strong> be closed
     * when done, typically via try-with-resources:
     *
     * <pre>{@code
     * try (ArtifactStream s = artifactRegistry.streamArtifact("large-model.onnx")) {
     *     session = OnnxRuntime.load(s.stream());
     * }
     * }</pre>
     *
     * <p>Unlike handles returned by {@link #getArtifact}, this object is not cached. Each call
     * to {@code streamArtifact} opens a new connection.
     *
     * @param manifest artifact metadata (version, content-type, sha256, labels, etc.)
     * @param stream   the raw artifact bytes as an open stream; caller must close
     */
    public record ArtifactStream(ArtifactManifest manifest, InputStream stream) implements Closeable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private ArtifactHandle fetchHandle(String owner, String artifactName, String alias) throws IOException {
        FetchedArtifact fetched = client.download(owner, artifactName, alias);
        if (fetched == null) {
            throw new ArtifactNotFoundException(owner, artifactName, alias);
        }
        return new DirectArtifactHandle(client, owner, artifactName, alias, fetched);
    }
}
