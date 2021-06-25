package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.types.ObjectId;
import org.deltafi.dgs.api.types.ConfigType;
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
@SuppressWarnings("unused")
public class TransformActionConfiguration extends org.deltafi.dgs.generated.types.TransformActionConfiguration implements DeltaFiConfiguration {

    private ConfigType configType = ConfigType.TRANSFORM_ACTION;
    private ObjectId id;

    @Override
    public ConfigType getConfigType() {
        return configType;
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
