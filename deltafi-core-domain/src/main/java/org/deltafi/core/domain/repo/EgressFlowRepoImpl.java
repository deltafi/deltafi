package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.types.EgressFlow;
import org.springframework.data.mongodb.core.MongoTemplate;

@SuppressWarnings("unused")
public class EgressFlowRepoImpl extends BaseFlowRepoImpl<EgressFlow> {

    public EgressFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, EgressFlow.class);
    }

}
