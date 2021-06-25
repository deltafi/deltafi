package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.types.ObjectId;
import org.deltafi.dgs.api.types.ConfigType;
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class EnrichActionConfiguration extends org.deltafi.dgs.generated.types.EnrichActionConfiguration implements DeltaFiConfiguration {

    private ConfigType configType = ConfigType.ENRICH_ACTION;
    private ObjectId id;

    public EnrichActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
    }

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
