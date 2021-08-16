package org.deltafi.dgs.api.types;

import org.deltafi.dgs.generated.types.ActionSchema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("actionSchema")
public class ActionSchemaImpl extends ActionSchema {

    @Id
    @Override
    public String getActionClass() {
        return super.getActionClass();
    }
}
