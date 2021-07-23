package org.deltafi.dgs.configuration;

import org.bson.types.ObjectId;
import org.deltafi.dgs.api.types.JsonMap;
import org.deltafi.dgs.generated.types.ActionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document("actions")
@CompoundIndex(name = "name", unique = true, def = "{name: 1}")
public interface ActionConfiguration {

    @Id
    ObjectId getId();
    String getName();
    String getType();
    JsonMap getParameters();
    OffsetDateTime getModified();
    ActionType getActionType();

    void setName(String name);
    void setModified(OffsetDateTime modified);
    void setParameters(JsonMap jsonMap);
}
