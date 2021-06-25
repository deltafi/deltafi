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
public class DomainEndpointConfiguration extends org.deltafi.dgs.generated.types.DomainEndpointConfiguration implements DeltaFiConfiguration {

    private ObjectId id;
    private ConfigType configType = ConfigType.DOMAIN_ENDPOINT;

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
    @Override
    public OffsetDateTime getCreated() {
        return getId().getDate().toInstant().atOffset(ZoneOffset.UTC);
    }

}
