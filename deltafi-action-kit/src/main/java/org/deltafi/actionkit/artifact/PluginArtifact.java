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

import org.springframework.beans.factory.annotation.Autowired;

import java.lang.annotation.*;

/**
 * Marks a field or parameter for injection of an {@link ArtifactHandle} from the
 * DeltaFi artifact registry.
 *
 * <p>Apply this annotation to any {@code ArtifactHandle}-typed field in a Spring-managed
 * bean. The action-kit auto-configuration wires up a custom
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver} that
 * intercepts the annotated injection point and resolves the artifact at startup:
 *
 * <pre>{@code
 * @PluginArtifact(artifactName = "my-model")
 * private ArtifactHandle model;
 * }</pre>
 *
 * <p>Resolution order is determined by {@link #scope()}. Missing artifacts are handled
 * based on the defaultResource setting}.
 */
@Autowired
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginArtifact {

    /**
     * The artifact identifier as stored in the registry.
     * Must be unique within the resolved namespace.
     */
    String artifactName();

    /**
     * Which storage namespace(s) to search when resolving this artifact.
     * Defaults to {@link ArtifactScope#PLUGIN_FIRST}: the plugin's own namespace is
     * tried first, then the shared namespace.
     */
    ArtifactScope scope() default ArtifactScope.PLUGIN_FIRST;

    /**
     * The alias (or literal version string) to resolve.
     * The special value {@code "active"} (the default) resolves to the version currently
     * tagged as active in the registry.
     */
    String alias() default "active";

    /**
     * Whether to resolve and download the artifact eagerly at application startup.
     * When {@code true} (the default), startup fails fast if the artifact cannot be resolved.
     * When {@code false}, resolution is deferred to the first call on the handle.
     */
    boolean preload() default true;

    /**
     * Whether to cache this artifact's bytes in memory.
     * When {@code false} (the default), the artifact is always fetched fresh from the registry on each
     * {@link ArtifactHandle#getBytes()} call; the shared cache entry is updated as a side effect
     * so that any co-located cacheable handles also see the latest content.
     * When {@code true}, the framework holds the bytes in the shared in-process
     * cache and only re-fetches when a version-change notification is received or the periodic
     * background poll detects a drift.
     */
    boolean cacheable() default false;

    /**
     * Classpath resource path to publish if the artifact does not yet exist in the registry.
     *
     * <p>When set, the framework publishes the resource as the first artifact version on startup
     * (if and only if the artifact is absent). The handle is immediately usable with the
     * seeded bytes and server-provided manifest metadata.
     *
     * <p>When blank (the default), startup is aborted with an {@link IllegalStateException}
     * if the artifact cannot be found.
     */
    String defaultResource() default "";

}
