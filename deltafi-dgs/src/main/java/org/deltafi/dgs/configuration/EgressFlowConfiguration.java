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
public class EgressFlowConfiguration extends org.deltafi.dgs.generated.types.EgressFlowConfiguration implements DeltaFiConfiguration {

    private ConfigType configType = ConfigType.EGRESS_FLOW;
    private ObjectId id;

    public EgressFlowConfiguration() {
        setValidateActions(new ArrayList<>());
        setIncludeIngressFlows(new ArrayList<>());
        setExcludeIngressFlows(new ArrayList<>());
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
