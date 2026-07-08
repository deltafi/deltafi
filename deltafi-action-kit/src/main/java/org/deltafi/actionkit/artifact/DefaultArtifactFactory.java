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

import io.valkey.JedisPool;
import org.deltafi.common.artifact.VersionChangedEvent;
import org.deltafi.common.http.HttpService;

import java.util.function.Consumer;

/**
 * Production {@link ArtifactFactory} — HTTP registry client, shared in-process cache, and an
 * optional Valkey version listener. Built via {@link #create}; declared-artifact resolution runs
 * through the real {@link ArtifactCacheManager}.
 */
class DefaultArtifactFactory implements ArtifactFactory {

    private final ArtifactCacheManager cacheManager;
    private final PluginArtifactFactory pluginArtifactFactory;
    private final ArtifactRegistryService registryService;

    /** Optional Valkey push-notification listener; {@code null} until {@link #attachVersionListener} runs. */
    private volatile ArtifactVersionChangeListener versionListener;

    private DefaultArtifactFactory(ArtifactCacheManager cacheManager,
            PluginArtifactFactory pluginArtifactFactory, ArtifactRegistryService registryService) {
        this.cacheManager = cacheManager;
        this.pluginArtifactFactory = pluginArtifactFactory;
        this.registryService = registryService;
    }

    static DefaultArtifactFactory create(String coreUrl, String pluginName, HttpService httpService,
            ArtifactCacheConfig cacheConfig) {
        ArtifactRegistryClient client = new ArtifactRegistryClient(coreUrl, pluginName, httpService);
        ArtifactCacheManager cacheManager = new ArtifactCacheManager(client, cacheConfig);
        return new DefaultArtifactFactory(
                cacheManager,
                new PluginArtifactFactory(cacheManager, pluginName),
                new DefaultArtifactRegistryService(client));
    }

    @Override
    public ArtifactFactory.Builder artifact(String artifactName) {
        return new ArtifactFactory.Builder(
                pluginArtifactFactory.pluginOwner(), artifactName, pluginArtifactFactory::create);
    }

    @Override
    public ArtifactRegistryService registryService() {
        return registryService;
    }

    @Override
    public void start() {
        cacheManager.fireStartupCallbacks();
        cacheManager.startPoller();
        if (versionListener != null) {
            versionListener.start();
        }
    }

    @Override
    public void stop() {
        if (versionListener != null) {
            versionListener.close();
        }
        cacheManager.close();
    }

    /**
     * Attach a Valkey pub/sub listener that pushes version-change events into the shared cache and
     * forwards them to {@code eventSink}. Wired by {@link ArtifactConfiguration}; non-Spring callers
     * that do not need push invalidation rely on the background poll alone.
     */
    void attachVersionListener(JedisPool subscriberPool, Consumer<VersionChangedEvent> eventSink) {
        this.versionListener = new ArtifactVersionChangeListener(subscriberPool, cacheManager, eventSink);
    }
}
