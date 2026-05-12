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
// ABOUTME: Transport interface for posting a plugin registration to the DeltaFi core.
// ABOUTME: HttpCoreClient is the standard implementation; embedded mode can register in-JVM.
package org.deltafi.actionkit.registration;

import org.deltafi.common.types.PluginRegistration;

/**
 * Posts a plugin registration to a DeltaFi core. Implementations: {@link HttpCoreClient} for the
 * standard HTTP path, or an in-JVM implementation for embedded mode where the action kit and
 * core share a process and registration can bypass the wire.
 */
public interface CoreClient extends AutoCloseable {

    /**
     * Submit the registration. Implementations are expected to throw on failure (after their own
     * retry policy, if any) so that the calling {@code PluginRegistrar} surfaces the failure to
     * the host framework's startup pipeline.
     */
    void register(PluginRegistration registration);

    /** Default no-op so most implementations don't need to override. */
    @Override
    default void close() {}
}
