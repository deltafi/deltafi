package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.client.RegisterEnrichSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterEnrichSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;

import java.util.Collections;
import java.util.List;

public abstract class EnrichAction<P extends ActionParameters> extends Action<P> {
    public EnrichAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.ENRICH);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterEnrichSchemaProjectionRoot().id();
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        EnrichActionSchemaInput paramInput = EnrichActionSchemaInput.newBuilder()
            .id(getClassCanonicalName())
            .paramClass(getParamClass())
            .actionKitVersion(getVersion())
            .schema(getDefinition())
            .requiresDomains(getRequiresDomains())
            .requiresEnrichment(getRequiresEnrichment())
            .build();
        return RegisterEnrichSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }
}
