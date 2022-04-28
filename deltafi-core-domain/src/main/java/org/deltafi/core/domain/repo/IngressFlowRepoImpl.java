package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.data.mongodb.core.MongoTemplate;

@SuppressWarnings("unused")
public class IngressFlowRepoImpl extends BaseFlowRepoImpl<IngressFlow> {

    public IngressFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, IngressFlow.class);
    }

}
