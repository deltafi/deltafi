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

import lombok.Getter;
import org.deltafi.common.artifact.VersionChangedEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} published whenever an artifact version changes.
 *
 * <p>Beans can subscribe using the standard Spring {@code @EventListener} mechanism:
 *
 * <pre>{@code
 * @EventListener
 * public void onArtifactChanged(ArtifactVersionChangedEvent event) {
 *     VersionChangedEvent change = event.getVersionChangedEvent();
 *     if ("my-model".equals(change.artifactName())) {
 *         rebuild();
 *     }
 * }
 * }</pre>
 *
 * <p>This event is fired for every version-change notification received from the Valkey
 * pub/sub channel, regardless of namespace (shared or plugin-scoped). Listeners that care
 * about namespace should inspect {@link VersionChangedEvent#prefix()} or
 * {@link VersionChangedEvent#isShared()}.
 *
 * <p>For a namespace-aware, handle-tied callback, use
 * {@link ArtifactHandle#onUpdate(java.util.function.Consumer)} instead.
 */
@Getter
public class ArtifactVersionChangedEvent extends ApplicationEvent {

    private final VersionChangedEvent versionChangedEvent;

    ArtifactVersionChangedEvent(Object source, VersionChangedEvent versionChangedEvent) {
        super(source);
        this.versionChangedEvent = versionChangedEvent;
    }
}
