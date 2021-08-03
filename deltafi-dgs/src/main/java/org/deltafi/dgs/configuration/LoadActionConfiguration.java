package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.minidev.json.annotate.JsonIgnore;
import org.deltafi.dgs.converters.KeyValueConverter;
import org.deltafi.dgs.generated.types.KeyValue;
import org.springframework.data.annotation.Transient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NONE
)
public class LoadActionConfiguration extends org.deltafi.dgs.generated.types.LoadActionConfiguration implements ActionConfiguration {

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
}
