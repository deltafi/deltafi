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
package org.deltafi.actionkit.registration;

import feign.Headers;
import feign.RequestLine;
import org.deltafi.common.types.PluginRegistration;

// ABOUTME: Feign client interface for registering plugins with the DeltaFi core.
// ABOUTME: Posts plugin registration data to the core REST API.

/**
 * Feign client for communicating with the DeltaFi core plugin registration endpoint
 */
public interface CoreClient {
    /**
     * Register a plugin with the core
     * @param pluginRegistration the plugin registration data to post
     */
    @RequestLine("POST /plugins")
    @Headers("Content-Type: application/json")
    void postPlugin(PluginRegistration pluginRegistration);
}
