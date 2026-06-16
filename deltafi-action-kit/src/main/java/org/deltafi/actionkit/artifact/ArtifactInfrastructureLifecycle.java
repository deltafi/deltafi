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

import org.springframework.context.SmartLifecycle;

/**
 * Spring adapter that drives the start/stop of the framework-agnostic {@link ArtifactFactory}.
 *
 * <p>Implemented as a {@link SmartLifecycle} at the default phase ({@code Integer.MAX_VALUE}) so it
 * starts after every other bean's {@code @PostConstruct} has run. This preserves the guarantee that
 * artifact startup callbacks fire only once all {@code onUpdate} registrations are in place.
 *
 * <p>Created by {@link ArtifactConfiguration} — not part of the public plugin API. Non-Spring
 * plugins drive {@link ArtifactFactory#start()} / {@link ArtifactFactory#close()} themselves.
 */
class ArtifactInfrastructureLifecycle implements SmartLifecycle {

    private final ArtifactFactory artifactFactory;

    private volatile boolean running;

    ArtifactInfrastructureLifecycle(ArtifactFactory artifactFactory) {
        this.artifactFactory = artifactFactory;
    }

    @Override
    public void start() {
        artifactFactory.start();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        artifactFactory.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
