package org.deltafi.action.transform;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.action.Result;
import org.deltafi.dgs.generated.client.TransformGraphQLQuery;
import org.deltafi.dgs.generated.client.TransformProjectionRoot;
import org.deltafi.dgs.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransformResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public TransformResult(TransformAction action, String did) {
        super(action, did);
    }

    protected final List<KeyValueInput> metadata = new ArrayList<>();
    protected ObjectReferenceInput objectReferenceInput;
    protected String type;

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        TransformGraphQLQuery.Builder builder = TransformGraphQLQuery.newRequest()
                .did(did)
                .protocolLayer(
                        new ProtocolLayerInput.Builder()
                                .metadata(metadata)
                                .type(type)
                                .objectReference(objectReferenceInput)
                                .build())
                .fromTransformAction(name);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new TransformProjectionRoot()
                .did();
    }

    public void addMetadata(@NotNull String key, @NotNull String value) {
        metadata.add(new KeyValueInput(key, value));
    }

    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }

    public void addMetadata(@NotNull KeyValue keyValue) {
        addMetadata(keyValue.getKey(), keyValue.getValue());
    }

    public void addMetadata(@NotNull List<KeyValue> keyValues) {
        keyValues.forEach(this::addMetadata);
    }

    public void setType(String type) {
        this.type = type;
    }
    public void setObjectReference(@NotNull ObjectReference objectReference) {
        objectReferenceInput = new ObjectReferenceInput.Builder()
                .bucket(objectReference.getBucket())
                .name(objectReference.getName())
                .size(objectReference.getSize())
                .offset(objectReference.getOffset())
                .build();
    }

    @SuppressWarnings("unused")
    public void setObjectReference(@NotNull String name, @NotNull String bucket, int size, int offset) {
        objectReferenceInput = new ObjectReferenceInput(name, bucket, offset, size);
    }

}