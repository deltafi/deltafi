package org.deltafi.action.egress;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Action;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.client.ActionFeedGraphQLQuery;
import org.deltafi.dgs.generated.client.ActionFeedProjectionRoot;

import java.util.HashMap;
import java.util.Map;

@Slf4j
abstract public class EgressAction implements Action {

    protected String name;
    protected final Map<String, String> staticMetadata = new HashMap<>();

    @Override
    public BaseProjectionNode getProjection() {
        return new ActionFeedProjectionRoot()
                .did()
                .domains()
                    .domainTypes()
                .parent()
                .enrichment()
                    .enrichmentTypes()
                .parent()
                .protocolStack()
                    .type()
                    .objectReference()
                        .size()
                        .offset()
                        .bucket()
                        .name()
                    .parent()
                    .metadata()
                        .key()
                        .value()
                    .parent()
                .parent()
                .formattedData()
                    .filename()
                    .formatAction()
                    .metadata()
                        .key()
                        .value()
                    .parent()
                    .objectReference()
                        .bucket()
                        .name()
                        .size()
                        .offset()
                    .parent()
                .parent()
                .sourceInfo()
                    .filename()
                    .flow()
                    .metadata()
                        .key()
                        .value()
                    .parent()
                .parent();
    }

    @Override
    public void init(DeltafiConfig.ActionSpec spec) {
        name = spec.name;
        Action.addStaticMetadata(spec, staticMetadata, log);
    }

    @Override
    public String name() {
        return name;
    }

    public String flow() {
        String flowName = name();
        // this should always be true
        if (flowName.endsWith("EgressAction")) {
            flowName = flowName.substring(0, flowName.length() - 12);
        }
        char[] flowNameChars = flowName.toCharArray();
        flowNameChars[0] = Character.toLowerCase(flowNameChars[0]);
        return new String(flowNameChars);
    }
}