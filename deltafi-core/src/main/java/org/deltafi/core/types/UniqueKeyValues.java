/**
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
package org.deltafi.core.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor
public class UniqueKeyValues {
    @JsonIgnore
    private final Set<String> valuesSet = new HashSet<>();

    @Getter
    private String key;

    public UniqueKeyValues(String key) {
        this.key = key;
    }

    public UniqueKeyValues(String key, List<String> values) {
        this.key = key;
        setValues(values);
    }

    public List<String> getValues() {
        return new ArrayList<>(valuesSet);
    }

    public void setValues(List<String> values) {
        valuesSet.addAll(values);
    }

    public void addValue(String value) {
        valuesSet.add(value);
    }
}
