package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.types.ObjectId;
import org.deltafi.dgs.generated.types.ActionType;
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class ValidateActionConfiguration extends org.deltafi.dgs.generated.types.ValidateActionConfiguration implements ActionConfiguration {

    private ActionType actionType = ActionType.VALIDATE_ACTION;
    private ObjectId id;

    @Override
    public ActionType getActionType() {
        return actionType;
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
