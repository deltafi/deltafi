package org.deltafi.core.domain.api.types;

import org.deltafi.core.domain.generated.types.ActionSchema;
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