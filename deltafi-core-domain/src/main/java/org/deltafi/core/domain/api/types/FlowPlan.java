package org.deltafi.core.domain.api.types;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("flowPlan")
public class FlowPlan extends org.deltafi.core.domain.generated.types.FlowPlan {

    @Id
    @Override
    public String getName() {
        return super.getName();
    }
}
