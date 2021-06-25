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
public class IngressFlowConfiguration extends org.deltafi.dgs.generated.types.IngressFlowConfiguration implements DeltaFiConfiguration {

    private ConfigType configType = ConfigType.INGRESS_FLOW;
    private ObjectId id;

    public IngressFlowConfiguration() {
        // loadActions variable is a list of candidate LoadActions that may operate on data in this flow
        setLoadActions(new ArrayList<>());
        // transformActions variable is an ordered list of the TransformActions that each data item will flow through
        setTransformActions(new ArrayList<>());
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
