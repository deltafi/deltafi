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

/**
 * Creates {@link ArtifactHandle} instances from {@link PluginArtifact} annotations.
 *
 * <p>Called by {@link PluginArtifactAutowireCandidateResolver} at Spring injection time for
 * every field annotated with {@code @PluginArtifact}. Depending on
 * {@link PluginArtifact#preload()}, the handle is either resolved immediately (eager) or
 * wrapped in a {@link LazyArtifactHandle} that defers resolution to first access.
 *
 * <p>Registered as a bean by the action-kit auto-configuration; should not normally need
 * to be instantiated directly by plugin code.
 */
class PluginArtifactFactory {

    private final ArtifactCacheManager cacheManager;
    private final String pluginName;

    /**
     * Create a factory for the given plugin.
     *
     * @param cacheManager the cache manager used to resolve and store artifact bytes
     * @param pluginOwner  the fully-qualified plugin owner ({@code group.artifactId})
     *                     used as the plugin namespace in the registry
     */
    PluginArtifactFactory(ArtifactCacheManager cacheManager, String pluginOwner) {
        this.cacheManager = cacheManager;
        this.pluginName = pluginOwner;
    }

    /**
     * Produce an {@link ArtifactHandle} for the injection point described by {@code annotation}.
     *
     * <p>When {@link PluginArtifact#preload()} is {@code true}, the artifact is resolved
     * (and bytes downloaded) immediately. When {@code false}, a lazy handle is returned and
     * resolution is deferred until the first call to {@link ArtifactHandle#getBytes()} ()} or similar.
     *
     * @param annotation the annotation on the injection point being processed
     * @return a handle to the artifact; never {@code null}
     * @throws IllegalStateException if {@code preload} is {@code true}, the artifact is not
     *                               found, and no {@link PluginArtifact#defaultResource()} is configured
     */
    ArtifactHandle create(PluginArtifact annotation) {
        ArtifactDescriptor descriptor = ArtifactDescriptor.from(pluginName, annotation);

        if (annotation.preload()) {
            return cacheManager.resolve(descriptor);
        }

        return new LazyArtifactHandle(descriptor.artifactName(), () -> cacheManager.resolve(descriptor));
    }
}
