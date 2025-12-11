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
// ABOUTME: Enum representing the installation state of a plugin.
// ABOUTME: Used by the reconciliation service to track desired vs actual state.
package org.deltafi.common.types;

/**
 * Represents the installation state of a plugin in the reconciliation model.
 */
public enum PluginState {
    /**
     * Plugin is desired but installation has not yet been attempted.
     * This is the initial state when a plugin is added via snapshot or UI.
     */
    PENDING,

    /**
     * Plugin installation is currently in progress.
     * The reconciler is actively deploying the container/pod.
     */
    INSTALLING,

    /**
     * Plugin is successfully installed and registered with core.
     * The container/pod is running and the plugin has registered its actions.
     */
    INSTALLED,

    /**
     * Plugin installation failed.
     * The error message is stored in the plugin entity.
     * Requires manual retry to attempt installation again.
     */
    FAILED,

    /**
     * Plugin is being removed.
     * The reconciler is actively stopping and removing the container/pod.
     */
    REMOVING
}
