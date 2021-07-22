package org.deltafi.actionkit.action.error;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.types.DeltaFile;
import org.deltafi.dgs.generated.client.ErrorGraphQLQuery;
import org.deltafi.dgs.generated.client.ErrorProjectionRoot;
import org.deltafi.dgs.generated.types.ErrorInput;
import org.deltafi.dgs.generated.types.ActionEventType;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ErrorResult extends Result {

    final String errorCause;
    final String errorContext;
    final private String errorSummary;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ErrorResult(Action action, DeltaFile deltafile, String errorMessage, Throwable throwable) {
        super(action, deltafile.getDid());
        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + deltafile.getDid() + "\n" + errorContext;
    }

    @SuppressWarnings("unused")
    public ErrorResult(Action action, DeltaFile deltafile, String errorMessage) {
        super(action, deltafile.getDid());
        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + deltafile.getDid();
    }

    public ErrorResult logErrorTo(Logger logger) {
        logger.error(errorSummary);
        return this;
    }

    @Override
    final public GraphQLQuery toQuery() {
        return ErrorGraphQLQuery.newRequest()
                .error(ErrorInput.newBuilder()
                        .originatorDid(did)
                        .fromAction(name)
                        .cause(errorCause)
                        .context(errorContext).build())
                .build();
    }

    @Override
    public BaseProjectionNode getProjection() {
        return new ErrorProjectionRoot()
                .did()
                .stage()
                .actions()
                    .name()
                    .errorCause()
                    .errorContext()
                    .state()
                .parent();
    }

    @Override
    final public ResultType resultType() { return ResultType.GRAPHQL; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.ERROR; }
}