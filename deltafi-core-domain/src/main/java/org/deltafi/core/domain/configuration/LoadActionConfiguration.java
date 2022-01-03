package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.minidev.json.annotate.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.converters.KeyValueConverter;
import org.springframework.data.annotation.Transient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NONE
)
public class LoadActionConfiguration extends org.deltafi.core.domain.generated.types.LoadActionConfiguration implements ActionConfiguration {

    @JsonIgnore
    private Map<String, String> requiresMetadata = new HashMap<>();

    @Override
    @Transient
    public List<KeyValue> getRequiresMetadataKeyValues() {
        return KeyValueConverter.fromMap(requiresMetadata);
    }

    public Map<String, String> getRequiresMetadata() {
        return requiresMetadata;
    }

    @SuppressWarnings("unused")
    public void setRequiresMetadata(Map<String, String> requiresMetadata) {
        this.requiresMetadata = requiresMetadata;
    }

    @Override
    public List<String> validate() {
        return StringUtils.isBlank(this.getConsumes()) ?
                List.of("Required property consumes is not set") : Collections.emptyList();
    }
}