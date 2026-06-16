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

import org.deltafi.common.http.HttpService;

import java.util.function.Function;

/**
 * Framework-agnostic entry point to the DeltaFi artifact registry.
 *
 * <p>{@code ArtifactFactory} is the construction seam for the artifact subsystem. Spring builds one
 * via {@link ArtifactConfiguration} and exposes it as a bean; non-Spring plugins build one directly
 * with {@link #create}. An action declares an artifact dependency with the fluent builder — the
 * non-Spring equivalent of the {@link PluginArtifact} annotation:
 * <pre>{@code
 * public MyTransform(ArtifactFactory artifacts) {
 *     this.model = artifacts.artifact("ml-model")
 *             .scope(ArtifactScope.SHARED_ONLY)
 *             .alias("v2.1")
 *             .cacheable(true)
 *             .build();
 * }
 * }</pre>
 *
 * <p>Because {@code ArtifactFactory} is an interface, an action that declares artifacts this way is
 * unit-testable by passing a {@code TestArtifactFactory} (from {@code deltafi-action-kit-test}) in
 * place of the production implementation — no running core required.
 *
 * <p>For ad-hoc publish and fetch, use {@link #registryService()}.
 */
public interface ArtifactFactory {

    /** Begin declaring an artifact dependency resolved through the shared in-process cache. */
    Builder artifact(String artifactName);

    /** The registry service for ad-hoc publish and fetch (and streaming) operations. */
    ArtifactRegistryService registryService();

    /** Start background freshness — the cache poller and, if wired, the version listener. */
    void start();

    /** Stop background freshness and release the poller and version-listener threads. */
    void stop();

    /**
     * Create a production artifact factory bound to a core URL and plugin identity.
     *
     * @param coreUrl     core base URL including {@code /api/v2}, e.g. {@code http://deltafi-core:8080/api/v2}
     * @param pluginName  this plugin's owner namespace ({@code group.artifactId})
     * @param httpService shared HTTP client
     */
    static ArtifactFactory create(String coreUrl, String pluginName, HttpService httpService) {
        return DefaultArtifactFactory.create(coreUrl, pluginName, httpService, new ArtifactCacheConfig());
    }

    /**
     * Fluent builder for an {@link ArtifactHandle}, mirroring the {@link PluginArtifact} annotation
     * parameters. A single concrete builder shared by every {@code ArtifactFactory} implementation:
     * the implementation supplies the {@code resolver} that turns the assembled
     * {@link ArtifactDescriptor} into a handle (a cache-backed resolve in production, a registered
     * fake in tests).
     */
    final class Builder {
        private final String pluginName;
        private final String artifactName;
        private final Function<ArtifactDescriptor, ArtifactHandle> resolver;
        private ArtifactScope scope = ArtifactScope.PLUGIN_FIRST;
        private String alias = "active";
        private boolean preload = true;
        private boolean cacheable = false;
        private String defaultResource = "";

        public Builder(String pluginName, String artifactName,
                Function<ArtifactDescriptor, ArtifactHandle> resolver) {
            this.pluginName = pluginName;
            this.artifactName = artifactName;
            this.resolver = resolver;
        }

        /** Search scope (default {@link ArtifactScope#PLUGIN_FIRST}). */
        public Builder scope(ArtifactScope scope) {
            this.scope = scope;
            return this;
        }

        /** Version alias to resolve (default {@code "active"}). */
        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        /** Resolve eagerly at {@link #build()} time (default {@code true}); {@code false} defers to first access. */
        public Builder preload(boolean preload) {
            this.preload = preload;
            return this;
        }

        /** Hold bytes in the shared cache between calls (default {@code false} — fetch fresh each call). */
        public Builder cacheable(boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        /** Classpath resource published as the first version if the artifact does not yet exist. */
        public Builder defaultResource(String classpathResource) {
            this.defaultResource = classpathResource;
            return this;
        }

        /** Resolve and return the configured handle. */
        public ArtifactHandle build() {
            return resolver.apply(new ArtifactDescriptor(
                    pluginName, artifactName, alias, scope, cacheable, defaultResource, preload));
        }
    }
}
