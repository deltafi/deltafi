package org.deltafi.actionkit.action;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.actionkit.types.DeltaFile;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public interface Action {
    String name();
    void init(DeltafiConfig.ActionSpec spec);
    Result execute(DeltaFile deltaFile);

    default Map<String, BaseProjectionNode> getDomainProjections() {
        return Collections.emptyMap();
    }

    default Map<String, BaseProjectionNode> getEnrichmentProjections() {
        return Collections.emptyMap();
    }

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