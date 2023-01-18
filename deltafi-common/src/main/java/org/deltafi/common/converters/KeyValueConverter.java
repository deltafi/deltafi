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
package org.deltafi.common.converters;

import org.deltafi.common.types.KeyValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

public class KeyValueConverter {

    public static Map<String, String> convertKeyValues(List<KeyValue> keyValues) {
        if (keyValues == null) {
            return new HashMap<>();
        }
        return keyValues.stream().collect(CustomCollector.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    public static List<KeyValue> fromMap(Map<String, String> map) {
        if (map == null) {
            return new ArrayList<>();
        }
        return map.entrySet().stream().map(KeyValueConverter::fromMapEntry).toList();
    }

    public static KeyValue fromMapEntry(Map.Entry<String, String> entry) {
        return new KeyValue(entry.getKey(), entry.getValue());
    }

    /**
     * This custom collector is needed to deal with the possibility that a value could be null.  The standard
     * collector will NPE.
     *
     * Scabbed from teh internets: https://kuros.in/java/streams/handle-nullpointerexception-in-collectors-tomap/
     */
    static class CustomCollector {
        public static <T, K, V> Collector<T, Map<K, V>, Map<K, V>> toMap(final Function<? super T, K> keyMapper, final Function<T, V> valueMapper) {
            return Collector.of(
                    HashMap::new,
                    (kvMap, t) -> kvMap.put(keyMapper.apply(t), valueMapper.apply(t)),
                    (kvMap, kvMap2) -> {
                        kvMap.putAll(kvMap2);
                        return kvMap;
                    },
                    Function.identity(),
                    Collector.Characteristics.IDENTITY_FINISH);
        }
    }
}