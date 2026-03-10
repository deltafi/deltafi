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
 * Controls which storage namespace(s) are searched when resolving a {@link PluginArtifact}.
 *
 * <p>Artifacts are stored under one of two prefixes:
 * <ul>
 *   <li><em>Plugin-scoped</em> — {@code plugins/<pluginName>/}: private to the owning plugin.
 *   <li><em>Shared</em> — {@code shared/}: visible to all plugins.
 * </ul>
 *
 * @see PluginArtifact#scope()
 */
public enum ArtifactScope {

    /**
     * Search the plugin's own namespace first; fall back to the shared namespace if not found.
     * This is the default and allows plugins to override shared artifacts with their own copies.
     */
    PLUGIN_FIRST,

    /**
     * Search only the plugin's own namespace; never fall back to shared.
     * Use when the artifact must be private to this plugin.
     */
    PLUGIN_ONLY,

    /**
     * Search only the shared namespace.
     * Use when the artifact is owned by the platform or another plugin and must not be shadowed.
     */
    SHARED_ONLY
}
