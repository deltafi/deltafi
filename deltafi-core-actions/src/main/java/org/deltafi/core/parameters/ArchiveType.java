/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ArchiveType {
    @JsonProperty("tar") TAR("tar", "application/x-tar"),
    @JsonProperty("zip") ZIP("zip", "application/x-zip"),
    @JsonProperty("ar") AR("ar", "application/x-archive"),
    @JsonProperty("tar.xz") TAR_XZ("tar.xz", "application/x-gtar"),
    @JsonProperty("tar.gz") TAR_GZIP("tar.gz", "application/x-gtar");

    private final String value;
    private final String mediaType;
}