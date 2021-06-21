package org.deltafi.action.format;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.action.Result;
import org.deltafi.dgs.generated.client.FormatGraphQLQuery;
import org.deltafi.dgs.generated.client.FormatProjectionRoot;
import org.deltafi.dgs.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FormatResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public FormatResult(FormatAction action, String did, String filename) {
        super(action, did);
        this.filename = filename;
    }

    protected String filename;
    protected final List<KeyValueInput> metadata = new ArrayList<>();
    protected ObjectReferenceInput objectReferenceInput;

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        FormatResultInput fri = new FormatResultInput.Builder()
                .filename(filename)
                .metadata(metadata)
                .objectReference(objectReferenceInput).build();

        FormatGraphQLQuery.Builder builder = FormatGraphQLQuery.newRequest()
                .did(did)
                .fromFormatAction(name)
                .formatResult(fri);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new FormatProjectionRoot()
                .did()
                .actions().errorMessage()
                .parent();
    }

    public void addMetadata(@NotNull String key, @NotNull String value) {
        metadata.add(new KeyValueInput(key, value));
    }

    @SuppressWarnings("unused")
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }

    public void addMetadata(@NotNull KeyValue keyValue) {
        addMetadata(keyValue.getKey(), keyValue.getValue());
    }

    public void addMetadata(@NotNull List<KeyValue> keyValues) {
        keyValues.forEach(this::addMetadata);
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

    @SuppressWarnings("unused")
    public String getFilename() { return filename; }

    @SuppressWarnings("unused")
    public void setFilename(String filename) { this.filename = filename; }
}