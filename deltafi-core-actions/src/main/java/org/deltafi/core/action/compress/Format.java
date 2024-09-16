/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.compress;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Format {
    @JsonProperty("ar") AR("ar", "application/x-archive"),
    @JsonProperty("7z") SEVEN_Z("7z", "application/x-7z-compressed"),
    @JsonProperty("tar") TAR("tar", "application/x-tar"),
    @JsonProperty("tar.gz") TAR_GZIP("tar.gz", "application/gzip"),
    @JsonProperty("tar.xz") TAR_XZ("tar.xz", "application/x-xz"),
    @JsonProperty("tar.Z") TAR_Z("tar.Z", "application/x-gtar"),
    @JsonProperty("zip") ZIP("zip", "application/zip"),

    @JsonProperty("gz") GZIP("gz", "application/gzip"),
    @JsonProperty("xz") XZ("xz", "application/x-xz"),
    @JsonProperty("z") Z("z", "application/x-compress");

    private final String value;
    private final String mediaType;
}
