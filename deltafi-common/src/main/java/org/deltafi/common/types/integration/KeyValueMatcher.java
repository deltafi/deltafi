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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class KeyValueMatcher {
    private String name;
    private String value;
    private Boolean exact;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isEmpty(name)) {
            errors.add("KeyValueMatcher missing name");
        }

        if (exact == null) {
            exact = false;
        }

        if (!exact && StringUtils.isEmpty(value)) {
            errors.add("KeyValueMatcher non-empty pattern value is required");
        } else if (value == null) {
            errors.add("KeyValueMatcher value is null");
        }

        return errors;
    }

    public Collection<String> matches(Map<String, String> actual, String label) {
        List<String> errors = new ArrayList<>();
        if (!actual.containsKey(name)) {
            errors.add(label + " is missing key/name: " + name);
        } else {
            String actualValue = actual.get(name);
            if (exact && !value.equals(actualValue)) {
                errors.add(label + " value of key " + name + " (" + actualValue
                        + ") does not match expected value: (" + value + ")");
            } else if (!exact) {
                if (!Pattern.matches(value, actualValue)) {
                    errors.add(label + " value of key " + name + " (" + actualValue
                            + ") does not match pattern: (" + value + ")");
                }
            }
        }
        return errors;
    }
}
