package org.deltafi.action;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.client.ActionFeedGraphQLQuery;
import org.deltafi.types.DeltaFile;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

public interface Action {
    String name();
    BaseProjectionNode getProjection();
    void init(DeltafiConfig.ActionSpec spec);
    Result execute(DeltaFile deltaFile);

    default GraphQLQuery getFeedQuery(Integer limit) {
        return ActionFeedGraphQLQuery.newRequest().action(name()).dryRun(false).limit(limit).build();
    }

    default String getFeedPath() { return getFeedQuery(0).getOperationName(); }

    static void addStaticMetadata(DeltafiConfig.ActionSpec spec, Map<String, String> staticMetadata, Logger log) {
        if (Objects.nonNull(spec.parameters) && spec.parameters.containsKey("static_metadata")) {
            try {
                Object sm = spec.parameters.get("static_metadata");
                @SuppressWarnings("unchecked")
                Map<String, Object> smm = (Map<String, Object>) sm;
                smm.forEach((k, v) -> staticMetadata.put(k, v.toString()));
            } catch (ClassCastException e) {
                log.error("static_metadata malformed in application properties");
            }
        }
    }

}