package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.minidev.json.annotate.JsonIgnore;
import org.bson.types.ObjectId;
import org.deltafi.dgs.converters.KeyValueConverter;
import org.deltafi.dgs.generated.types.ActionType;
import org.deltafi.dgs.generated.types.KeyValue;
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NONE
)
public class LoadActionConfiguration extends org.deltafi.dgs.generated.types.LoadActionConfiguration implements ActionConfiguration {

    @JsonIgnore
    private Map<String, String> requiresMetadata = new HashMap<>();
    private ActionType actionType = ActionType.LOAD_ACTION;
    private ObjectId id;

    @Override
    public ActionType getActionType() {
        return actionType;
    }

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
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    @Transient
    public OffsetDateTime getCreated() {
        return getId().getDate().toInstant().atOffset(ZoneOffset.UTC);
    }
}
