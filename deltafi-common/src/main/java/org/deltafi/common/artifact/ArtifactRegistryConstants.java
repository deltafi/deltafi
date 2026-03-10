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
package org.deltafi.common.artifact;

/**
 * Shared constants for the DeltaFi artifact registry, referenced by both
 * the core server and the action-kit client.
 */
public final class ArtifactRegistryConstants {

    /** The alias that points to the currently active version of an artifact. */
    public static final String ACTIVE_ALIAS = "active";

    /** The owner name used for organization-wide shared artifacts. */
    public static final String SHARED_OWNER = "shared";

    /** Base path for all artifact registry REST endpoints (relative to {@code /api/v2}). */
    public static final String ARTIFACTS_BASE_PATH = "/artifacts";

    /** Valkey pub/sub channel on which version-change events are broadcast. */
    public static final String VERSION_CHANGED_CHANNEL = "deltafi:artifact:version-changed";

    private ArtifactRegistryConstants() {}
}
