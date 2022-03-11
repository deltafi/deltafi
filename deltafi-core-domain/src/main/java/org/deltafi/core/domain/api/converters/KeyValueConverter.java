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
