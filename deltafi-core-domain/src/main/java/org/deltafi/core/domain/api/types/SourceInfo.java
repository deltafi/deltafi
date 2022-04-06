package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceInfo {
    private String filename;
    private String flow;
    private List<KeyValue> metadata;

    @JsonIgnore
    public Map<String, String> getMetadataAsMap() {
        return KeyValueConverter.convertKeyValues(metadata);
    }

    @JsonIgnore
    public String getMetadata(String key) {
        return getMetadata(key, null);
    }

    @JsonIgnore
    public String getMetadata(String key, String defaultValue) {
        return metadata.stream().filter(k-> k.getKey().equals(key)).findFirst().map(KeyValue::getValue).orElse(defaultValue);
    }

    @JsonIgnore
    public void addMetadata(KeyValue keyValue) {
        metadata.add(keyValue);
    }

    @JsonIgnore
    public void addMetadata(List<KeyValue> keyValues) {
        if (keyValues == null) { return; }
        metadata.addAll(keyValues);
    }

    @JsonIgnore
    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }

    @JsonIgnore
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }
}
