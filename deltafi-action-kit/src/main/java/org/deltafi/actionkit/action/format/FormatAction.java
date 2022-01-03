package org.deltafi.actionkit.action.format;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.generated.client.RegisterFormatSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterFormatSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class FormatAction<P extends ActionParameters> extends Action<P> {
    public FormatAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterFormatSchemaProjectionRoot().id();
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

    protected void addSourceInputMetadata(FormatResult result, DeltaFile deltaFile) {
        if (deltaFile.getSourceInfo() != null && deltaFile.getSourceInfo().getMetadata() != null) {
            deltaFile.getSourceInfo().getMetadata()
                    .forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        }
    }

    protected void addProtocolStackMetadata(FormatResult result, DeltaFile deltaFile) {
        if (deltaFile.getProtocolStack() != null) {
            deltaFile.getProtocolStack().stream()
                    .filter(protocolLayer -> !Objects.isNull(protocolLayer.getMetadata()))
                    .forEach(protocolLayer-> addMetadata(result, protocolLayer.getMetadata()));
        }
    }

    private void addMetadata(FormatResult result, List<KeyValue> keyValues) {
        keyValues.forEach(keyValue -> addMetadata(result, keyValue));
    }

    private void addMetadata(FormatResult result, KeyValue keyValue) {
        result.addMetadata(keyValue.getKey(), keyValue.getValue());
    }
}
