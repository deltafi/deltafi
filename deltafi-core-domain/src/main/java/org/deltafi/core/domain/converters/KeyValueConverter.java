package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.api.types.KeyValue;

import java.util.*;
import java.util.stream.Collectors;

public class KeyValueConverter {

    public static Map<String, String> convertKeyValues(List<KeyValue> keyValues) {
        if (Objects.isNull(keyValues)) {
            return Collections.emptyMap();
        }
        Map<String, String> keyValueMap = new HashMap<>();
        for (KeyValue keyValue : keyValues) {
            keyValueMap.put(keyValue.getKey(), keyValue.getValue());
        }

        return keyValueMap;
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