package org.deltafi.core.action.delete;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.client.RegisterDeleteSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterDeleteSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.DeleteActionSchemaInput;
import org.jetbrains.annotations.NotNull;

public class DeleteAction extends Action<ActionParameters> {
    public DeleteAction() {
        super(ActionParameters.class);
    }

    @Override
    public Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull ActionParameters params) {
        if (!deleteContent(deltaFile.getDid())) {
            return new ErrorResult(context, "Unable to delete all objects for delta file.");
        }

        return new DeleteResult(context);
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        DeleteActionSchemaInput paramInput = DeleteActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .actionKitVersion(getVersion())
                .schema(getDefinition())
                .build();
        return RegisterDeleteSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterDeleteSchemaProjectionRoot().id();
    }
}
