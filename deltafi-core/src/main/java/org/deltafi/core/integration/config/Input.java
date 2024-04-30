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
package org.deltafi.core.integration.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Data
public class Input {
    // Flow for ingress
    private String flow;
    // Optional content type
    private String contentType;
    // File name to use for ingress
    private String ingressFileName;
    // true:" "data: !!binary | ..."
    private boolean base64Encoded;
    private String data;

    // TODO: Metadata map?
    private Duration timeout;

    public List<String> validate(List<String> flows) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            data = ""; // No input data is OK
        }

        if (StringUtils.isEmpty(flow)) {
            errors.add("Input must specify the 'flow'");
        } else {
            if (flows == null || !flows.contains(flow)) {
                errors.add("Input flow must exist in configuration flows");
            }
        }

        if (StringUtils.isEmpty(ingressFileName)) {
            errors.add("Missing or empty ingressFileName");
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

    public ByteArrayInputStream getByteArrayInputStream() {
        if (base64Encoded) {
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            return new ByteArrayInputStream(decodedBytes);
        } else {
            return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        }
    }
}
