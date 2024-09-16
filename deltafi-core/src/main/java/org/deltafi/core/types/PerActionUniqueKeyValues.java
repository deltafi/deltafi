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
package org.deltafi.core.types;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor
public class PerActionUniqueKeyValues {
    private final Map<String, UniqueKeyValues> keyVals = new HashMap<>();

    @Getter
    private String flow;

    @Getter
    private String action;

    public PerActionUniqueKeyValues(String flow, String action) {
        this.flow = flow;
        this.action = action;
    }

    public List<UniqueKeyValues> getKeyVals() {
        return new ArrayList<>(keyVals.values());
    }

    @SuppressWarnings("unused")
    public void setKeyVals(List<UniqueKeyValues> setKetVals) {
        for(UniqueKeyValues kv : setKetVals) {
            if (keyVals.containsKey(kv.getKey())) {
                keyVals.get(kv.getKey()).addValues(kv.getValues());
            } else {
                keyVals.put(kv.getKey(), new UniqueKeyValues(kv.getKey(), kv.getValues()));
            }
        }
    }

    public void addValue(String key, String value) {
        if (!keyVals.containsKey(key)) {
            keyVals.put(key, new UniqueKeyValues(key));
        }

        keyVals.get(key).addValue(value);
    }
}
