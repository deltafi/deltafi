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
package org.deltafi.core.types.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.KeyValue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseIngress {
    // Flow for ingress
    private String flow;
    // Optional content type
    private String contentType;
    // File name to use for ingress
    private String ingressFileName;
    // true:" "data: !!binary | ..."
    private Boolean base64Encoded;
    private String data;
    private List<KeyValue> metadata;

    public List<String> validate(List<String> flows) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            data = ""; // No input data is OK
        }

        if (StringUtils.isEmpty(flow)) {
            errors.add("TestCaseIngress must specify the 'dataSource'");
        } else {
            if (flows == null || !flows.contains(flow)) {
                errors.add("TestCaseIngress dataSource must exist in configuration flows");
            }
        }

        if (StringUtils.isEmpty(ingressFileName)) {
            errors.add("Missing or empty ingressFileName");
        }

        if (base64Encoded == null) {
            base64Encoded = false;
        }

        if (base64Encoded) {
            try {
                Base64.getDecoder().decode(data);
            } catch (Exception e) {
                errors.add("Failed to base64-decode: " + data);
            }
        }

        return errors;
    }

    public ByteArrayInputStream dataAsInputStream() {
        if (base64Encoded) {
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            return new ByteArrayInputStream(decodedBytes);
        } else {
            return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    public Map<String, String> metadataToMap() {
        return KeyValueConverter.convertKeyValues(metadata);
    }
}
