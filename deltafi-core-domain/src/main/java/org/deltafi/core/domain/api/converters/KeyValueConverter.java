/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.api.converters;

import org.deltafi.core.domain.api.types.KeyValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class KeyValueConverter {

    public static Map<String, String> convertKeyValues(List<KeyValue> keyValues) {
        if (Objects.isNull(keyValues)) {
            return Collections.emptyMap();
        }
        return keyValues.stream().collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    public static List<KeyValue> fromMap(Map<String, String> map) {
        if (Objects.isNull(map)) {
            return Collections.emptyList();
        }
        return map.entrySet().stream().map(KeyValueConverter::fromMapEntry).collect(Collectors.toList());
    }

    public static KeyValue fromMapEntry(Map.Entry<String, String> entry) {
        return new KeyValue(entry.getKey(), entry.getValue());
    }

}
