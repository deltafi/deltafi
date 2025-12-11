/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types.snapshot;

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.PluginEntity;

public record PluginSnapshot(String imageName, String imagePullSecret, PluginCoordinates pluginCoordinates, Boolean disabled) {
    public PluginSnapshot(PluginEntity pluginEntity) {
        this(pluginEntity.imageAndTag(), pluginEntity.getImagePullSecret(), pluginEntity.getPluginCoordinates(), pluginEntity.isDisabled());
    }

    /**
     * Constructor for backward compatibility with old snapshots missing disabled field.
     */
    public PluginSnapshot(String imageName, String imagePullSecret, PluginCoordinates pluginCoordinates) {
        this(imageName, imagePullSecret, pluginCoordinates, false);
    }

    /**
     * Returns whether the plugin is disabled, defaulting to false for old snapshots.
     */
    public boolean isDisabled() {
        return disabled != null && disabled;
    }
}
