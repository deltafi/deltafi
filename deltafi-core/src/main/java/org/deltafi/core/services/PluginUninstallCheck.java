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
package org.deltafi.core.services;

import org.deltafi.core.types.PluginEntity;

public interface PluginUninstallCheck {

    /**
     * Pre-uninstall check that will run prior to a plugin removal.
     * Return a message stating why the plugin cannot be uninstalled
     * or null if the uninstall can be performed.
     *
     * @param plugin that will be removed if there are no blockers
     * @return reason this plugin cannot be uninstalled
     */
    String uninstallBlockers(PluginEntity plugin);
}
