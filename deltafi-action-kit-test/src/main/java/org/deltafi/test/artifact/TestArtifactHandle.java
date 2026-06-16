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
package org.deltafi.test.artifact;

import org.deltafi.actionkit.artifact.ArtifactHandle;
import org.deltafi.actionkit.artifact.ArtifactManifest;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.artifact.VersionChangedEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A test-friendly {@link ArtifactHandle} built with a fluent builder.
 *
 * <p>Create from a literal string:
 * <pre>{@code
 * ArtifactHandle rules = TestArtifactHandle.fromString("allow: *")
 *         .version("1.0")
 *         .description("default ruleset")
 *         .build();
 * }</pre>
 *
 * <p>Or from a classpath resource:
 * <pre>{@code
 * ArtifactHandle model = TestArtifactHandle.fromResource("test-data/model.bin")
 *         .version("20240101")
 *         .shared()
 *         .build();
 * }</pre>
 *
 * <p>To exercise {@link #onUpdate} callbacks registered by the code under test:
 * <pre>{@code
 * handle.fireUpdate(someVersionChangedEvent);
 * }</pre>
 */
public class TestArtifactHandle implements ArtifactHandle {

    private final byte[] bytes;
    private final String artifactName;
    private final String version;
    private final String description;
    private final String contentType;
    private final String sha256;
    private final Map<String, String> labels;
    private final String sourcePrefix;
    private final boolean ready;
    private final Clock clock;

    private final List<Consumer<VersionChangedEvent>> callbacks = new ArrayList<>();

    // @Builder on the constructor keeps `callbacks` out of the builder automatically.
    // Defaults for sourcePrefix, ready, and clock are applied via null-check since @Builder.Default
    // is not supported on constructor parameters.
    @lombok.Builder(builderClassName = "Builder")
    private TestArtifactHandle(byte[] bytes, String artifactName, String version, String description,
                               String contentType, String sha256, Map<String, String> labels,
                               String sourcePrefix, Boolean ready, Clock clock) {
        this.bytes        = bytes;
        this.artifactName = artifactName;
        this.version      = version;
        this.description  = description;
        this.contentType  = contentType;
        this.sha256       = sha256;
        this.labels       = labels;
        this.sourcePrefix = sourcePrefix != null ? sourcePrefix : "plugins/test";
        this.ready        = ready == null || ready;
        this.clock        = clock != null ? clock : Clock.systemUTC();
    }

    // ── Factory entry points ──────────────────────────────────────────────────

    /** Start building a handle whose content is the UTF-8 encoding of {@code content}. */
    public static Builder fromString(String content) {
        return builder().bytes(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Start building a handle backed by the given raw bytes. */
    public static Builder fromBytes(byte[] bytes) {
        return builder().bytes(bytes.clone());
    }

    /**
     * Start building a handle whose content is loaded from a classpath resource.
     *
     * @param resourcePath path relative to the classpath root, e.g. {@code "test-data/model.bin"}
     * @throws IllegalArgumentException if the resource is not found on the classpath
     * @throws UncheckedIOException     if the resource cannot be read
     */
    public static Builder fromResource(String resourcePath) {
        try (InputStream in = TestArtifactHandle.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
            }
            return builder().bytes(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read classpath resource: " + resourcePath, e);
        }
    }

    // ── ArtifactHandle ────────────────────────────────────────────────────────

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public ArtifactManifest getManifest() {
        return new ArtifactManifest(version, description, contentType, sha256, labels);
    }

    @Override
    public Map<String, String> getLabels() {
        return labels;
    }

    @Override
    public String getSha256() {
        return sha256;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public boolean isDefault() {
        return labels != null && "true".equals(labels.get("default"));
    }

    @Override
    public String getSourcePrefix() {
        return sourcePrefix;
    }

    @Override
    public boolean isShared() {
        return "shared".equals(sourcePrefix);
    }

    @Override
    public void refresh() {
        // no-op — content is fixed at construction time in tests
    }

    @Override
    public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
        callbacks.add(callback);
        return this;
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /**
     * Invokes all {@link #onUpdate} callbacks registered by the code under test,
     * passing {@code event} to each. Use this to simulate a version-change notification.
     *
     * @param event the event to deliver to each registered callback
     */
    public void fireUpdate(VersionChangedEvent event) {
        for (Consumer<VersionChangedEvent> cb : callbacks) {
            cb.accept(event);
        }
    }

    /**
     * Invokes all {@link #onUpdate} callbacks with a synthesized event built from this handle's
     * own state — {@code prefix=sourcePrefix}, {@code alias="active"},
     * {@code previousVersion=null}, {@code newVersion=version},
     * {@code timestamp=clock.instant()}. Mirrors the production
     * {@code ArtifactCacheManager.fireStartupCallbacks()} seeding event.
     *
     * <p>Supply a {@link Clock} via the builder if a test needs deterministic timestamps.
     */
    public void fireUpdate() {
        fireUpdate(VersionChangedEvent.builder()
                .prefix(sourcePrefix)
                .artifactName(artifactName)
                .alias(ArtifactRegistryConstants.ACTIVE_ALIAS)
                .previousVersion(null)
                .newVersion(version)
                .timestamp(clock.instant())
                .build());
    }

    // ── Builder extras ────────────────────────────────────────────────────────

    // Lombok generates all the standard builder methods into this class.
    // We add shared() as a convenience alias for sourcePrefix("shared").
    public static class Builder {

        /**
         * Marks this artifact as originating from the shared namespace
         * ({@code sourcePrefix = "shared"}, {@link ArtifactHandle#isShared()} returns {@code true}).
         */
        public Builder shared() {
            return sourcePrefix("shared");
        }
    }
}
