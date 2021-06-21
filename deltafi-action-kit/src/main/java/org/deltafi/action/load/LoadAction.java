package org.deltafi.action.load;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Action;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.client.ActionFeedGraphQLQuery;
import org.deltafi.dgs.generated.client.ActionFeedProjectionRoot;

@Slf4j
abstract public class LoadAction implements Action {

    protected String name;

    @Override
    public BaseProjectionNode getProjection() {
        return new ActionFeedProjectionRoot()
                .did()
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
    }

    @Override
    public String name() {
        return name;
    }
}