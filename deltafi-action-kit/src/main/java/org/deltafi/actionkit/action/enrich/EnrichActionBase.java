package org.deltafi.actionkit.action.enrich;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.client.RegisterEnrichSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterEnrichSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class EnrichActionBase<P extends ActionParameters> extends Action<P> {
    public EnrichActionBase(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
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

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterEnrichSchemaProjectionRoot().id();
    }
}
