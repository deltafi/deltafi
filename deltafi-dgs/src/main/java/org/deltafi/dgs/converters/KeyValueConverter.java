package org.deltafi.dgs.converters;

import org.deltafi.dgs.generated.types.KeyValue;
import org.deltafi.dgs.generated.types.KeyValueInput;

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

    public static Map<String, String> convertKeyValueInputs(List<KeyValueInput> keyValues) {
        if (Objects.isNull(keyValues)) {
            return Collections.emptyMap();
        }
        Map<String, String> keyValueMap = new HashMap<>();
        for (KeyValueInput keyValue : keyValues) {
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
        return KeyValue.newBuilder().key(entry.getKey()).value(entry.getValue()).build();
    }

}
