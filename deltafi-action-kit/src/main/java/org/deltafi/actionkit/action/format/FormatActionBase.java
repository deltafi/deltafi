package org.deltafi.actionkit.action.format;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.client.RegisterFormatSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterFormatSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;

import java.util.Collections;
import java.util.List;

public abstract class FormatActionBase<P extends ActionParameters> extends Action<P> {
    public FormatActionBase(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        FormatActionSchemaInput paramInput = FormatActionSchemaInput.newBuilder()
            .id(getClassCanonicalName())
            .paramClass(getParamClass())
            .actionKitVersion(getVersion())
            .schema(getDefinition())
            .requiresDomains(getRequiresDomains())
            .requiresEnrichment(getRequiresEnrichment())
            .build();
        return RegisterFormatSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterFormatSchemaProjectionRoot().id();
    }
}
