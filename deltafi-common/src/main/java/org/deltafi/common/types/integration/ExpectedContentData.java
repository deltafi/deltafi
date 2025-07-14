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
package org.deltafi.common.types.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.KeyValue;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedContentData {
    // required
    private String name;
    // optional
    private String mediaType;
    // Exactly one of 'value' or 'contains' is required
    private List<String> contains;
    // Fields below only used for 'value' comparison
    private String value;
    // Decode 'value' from base64
    private Boolean base64Encoded;
    // Removes all white-space before comparison
    private Boolean ignoreWhitespace;
    // Substitute '{{DID}}' and '{{PARENT_DID}}' in the expected output with the actual values
    private Boolean macroSubstitutions;
    // Substitute RegEx 'key' with plain-text 'value' in the action generated content
    private List<KeyValue> extraSubstitutions;

    public Collection<String> validate() {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isEmpty(name)) {
            errors.add("ExpectedContentData missing 'name'");
        }

        if (StringUtils.isEmpty(value) && (contains == null || contains.isEmpty())) {
            errors.add("ExpectedContentData missing 'value' or 'contains'");
        } else if (!StringUtils.isEmpty(value) && contains != null && !contains.isEmpty()) {
            errors.add("ExpectedContentData must contain only one of 'value' or 'contains'");
        }

        if (ignoreWhitespace == null) {
            ignoreWhitespace = false;
        }

        if (macroSubstitutions == null) {
            macroSubstitutions = false;
        }

        if (base64Encoded == null) {
            base64Encoded = false;
        }

        if (base64Encoded) {
            try {
                Base64.getDecoder().decode(value);
            } catch (Exception e) {
                errors.add("Failed to base64-decode: " + value);
            }
        }

        if (extraSubstitutions != null) {
            for (KeyValue substitution : extraSubstitutions) {
                if (StringUtils.isBlank(substitution.getKey())) {
                    errors.add("Invalid extraSubstitutions key");
                    break;
                }
            }
        }

        return errors;
    }

    protected String normalize(String did, String parentDid) {
        String normalized = value;
        if (base64Encoded) {
            byte[] decodedBytes = Base64.getDecoder().decode(value);
            normalized = new String(decodedBytes, StandardCharsets.UTF_8);
        }
        if (macroSubstitutions) {
            normalized = normalized.replace("{{DID}}", did);
            if (parentDid != null) {
                normalized = normalized.replace("{{PARENT_DID}}", parentDid);
            }
        }
        return ignoreWhitespace ? StringUtils.deleteWhitespace(normalized) : normalized;
    }

    public String checkIfEquivalent(String input, String did, String parentDid) {
        if (contains != null && !contains.isEmpty()) {
            List<String> missing = getContains().stream()
                    .filter(stringToContain -> !input.contains(stringToContain))
                    .toList();
            if (!missing.isEmpty()) {
                return "Missing expected content: " + String.join(",", missing);
            }
        } else {
            String updatedInput = input;
            Map<String, String> substitutions = extraSubstitutionsMap();
            if (!substitutions.isEmpty()) {
                for (Map.Entry<String, String> entry : substitutions.entrySet()) {
                    updatedInput = updatedInput.replaceAll(entry.getKey(), entry.getValue());
                }
            }

            final String actualContent = ignoreWhitespace ? StringUtils.deleteWhitespace(updatedInput) : updatedInput;
            String expectedValue = normalize(did, parentDid);
            if (!expectedValue.equals(actualContent)) {
                return "[" + name + "] Expected  content to be '" + expectedValue + "', but was '" + actualContent + "'";
            }
        }
        return null;
    }

    public Map<String, String> extraSubstitutionsMap() {
        return KeyValueConverter.convertKeyValues(extraSubstitutions);
    }
}
