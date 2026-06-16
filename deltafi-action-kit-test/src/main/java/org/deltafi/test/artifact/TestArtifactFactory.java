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

import org.deltafi.actionkit.artifact.ArtifactDescriptor;
import org.deltafi.actionkit.artifact.ArtifactFactory;
import org.deltafi.actionkit.artifact.ArtifactHandle;
import org.deltafi.actionkit.artifact.ArtifactRegistryService;

import java.util.HashMap;
import java.util.Map;

/**
 * A test-friendly {@link ArtifactFactory} for unit-testing actions that self-declare artifacts via
 * {@code artifactFactory.artifact("name")...build()} in their constructor.
 *
 * <p>Lenient by default — any declared artifact resolves to an empty handle, so an action whose
 * artifact content is incidental to the test needs no setup at all:
 * <pre>{@code
 * new MyTransform(new TestArtifactFactory());
 * }</pre>
 *
 * <p>Register content for the artifacts the test actually asserts on:
 * <pre>{@code
 * ArtifactFactory artifacts = TestArtifactFactory.builder()
 *         .artifact("extra.txt", "hello from extra.txt")
 *         .build();
 * new MyTransform(artifacts);
 * }</pre>
 *
 * <p>Resolution returns the registered (or empty) {@link TestArtifactHandle} directly — it does not
 * run the real cache, HTTP, or scope logic. That keeps action tests fast and dependency-free; the
 * resolution machinery has its own unit tests.
 */
public class TestArtifactFactory implements ArtifactFactory {

    private static final String TEST_PLUGIN = "test";

    private final Map<String, ArtifactHandle> handles;
    private final ArtifactRegistryService registryService;

    private TestArtifactFactory(Map<String, ArtifactHandle> handles, ArtifactRegistryService registryService) {
        this.handles = handles;
        this.registryService = registryService;
    }

    /** A lenient factory with no registered artifacts — every declared artifact resolves to an empty handle. */
    public TestArtifactFactory() {
        this(Map.of(), null);
    }

    /** Start building a factory with specific artifact content (and optionally a registry service). */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ArtifactFactory.Builder artifact(String artifactName) {
        return new ArtifactFactory.Builder(TEST_PLUGIN, artifactName, this::resolve);
    }

    private ArtifactHandle resolve(ArtifactDescriptor descriptor) {
        return handles.getOrDefault(descriptor.artifactName(),
                TestArtifactHandle.fromBytes(new byte[0]).artifactName(descriptor.artifactName()).build());
    }

    @Override
    public ArtifactRegistryService registryService() {
        if (registryService == null) {
            throw new IllegalStateException("No ArtifactRegistryService configured on this " +
                    "TestArtifactFactory — supply one via TestArtifactFactory.builder().registryService(...)");
        }
        return registryService;
    }

    @Override
    public void start() {
        // no-op — nothing runs in the background in tests
    }

    @Override
    public void stop() {
        // no-op
    }

    /** Builder for registering specific artifact content (and optionally a registry service). */
    public static final class Builder {
        private final Map<String, ArtifactHandle> handles = new HashMap<>();
        private ArtifactRegistryService registryService;

        /** Register a UTF-8 string as the content of {@code artifactName}. */
        public Builder artifact(String artifactName, String content) {
            handles.put(artifactName, TestArtifactHandle.fromString(content).artifactName(artifactName).build());
            return this;
        }

        /** Register an explicit handle for {@code artifactName}. */
        public Builder artifact(String artifactName, ArtifactHandle handle) {
            handles.put(artifactName, handle);
            return this;
        }

        /** Supply the service returned by {@link TestArtifactFactory#registryService()} (e.g. a mock). */
        public Builder registryService(ArtifactRegistryService registryService) {
            this.registryService = registryService;
            return this;
        }

        public TestArtifactFactory build() {
            return new TestArtifactFactory(Map.copyOf(handles), registryService);
        }
    }
}
