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
package org.deltafi.common.types.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedContentData {
    // required
    private String name;
    // optional
    private String mediaType;
    // exactly one of value or contains is required
    private String value;
    private List<String> contains;

    public Collection<String> validate() {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isEmpty(name)) {
            errors.add("ExpectedContentData missing 'name'");
        }
        if (StringUtils.isEmpty(value) &&
                (contains == null || contains.isEmpty())) {
            errors.add("ExpectedContentData missing 'value' or 'contains'");
        } else if (!StringUtils.isEmpty(value) &&
                contains != null && !contains.isEmpty()) {
            errors.add("ExpectedContentData must contain only one of 'value' or 'contains'");
        }

        return errors;
    }
}
