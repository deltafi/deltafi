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
package org.deltafi.core.artifact;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Summary entry for a single artifact in the list-all response. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtifactSummary {

    private String artifactName;
    private String activeVersion;
    private ArtifactManifest activeManifest;
    private int totalVersions;
    /** All aliases for this artifact, including {@code active}. Map of alias name → version string. */
    private Map<String, String> aliases;
}
