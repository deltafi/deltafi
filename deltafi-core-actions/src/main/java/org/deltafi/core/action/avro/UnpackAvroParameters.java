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
package org.deltafi.core.action.avro;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UnpackAvroParameters extends ContentSelectionParameters {
    @JsonProperty(defaultValue = "NDJSON")
    @JsonPropertyDescription("How decoded Avro records should be written: one newline-delimited JSON file, one JSON array file, or multiple numbered JSON files")
    private AvroRecordOutputMode recordOutputMode = AvroRecordOutputMode.NDJSON;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Whether to write the Avro schema as a JSON content")
    private boolean includeSchemaFile = false;

    @JsonProperty(defaultValue = ".schema.json")
    @JsonPropertyDescription("Suffix used when includeSchemaFile is true")
    private String schemaFilenameSuffix = ".schema.json";

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Whether to write container metadata as a JSON content")
    private boolean includeContainerMetadataFile = true;

    @JsonProperty(defaultValue = ".container-metadata.json")
    @JsonPropertyDescription("Suffix used when includeContainerMetadataFile is true")
    private String containerMetadataFilenameSuffix = ".container-metadata.json";

    @JsonProperty(defaultValue = ".content.ndjson")
    @JsonPropertyDescription("Suffix used when recordOutputMode is NDJSON")
    private String contentSuffix = ".content.ndjson";

    @JsonProperty(defaultValue = ".content.json")
    @JsonPropertyDescription("Suffix used when recordOutputMode is JSON_ARRAY")
    private String jsonArraySuffix = ".content.json";

    @JsonProperty(defaultValue = ".")
    @JsonPropertyDescription("Separator inserted before the numbered file index when recordOutputMode is INDIVIDUAL_JSON")
    private String individualRecordSeparator = ".";

    @JsonProperty(defaultValue = ".json")
    @JsonPropertyDescription("Suffix used for each numbered record file when recordOutputMode is INDIVIDUAL_JSON")
    private String individualRecordSuffix = ".json";
}
