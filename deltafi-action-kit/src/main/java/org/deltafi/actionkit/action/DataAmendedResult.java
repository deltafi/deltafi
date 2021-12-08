package org.deltafi.actionkit.action;

import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.KeyValue;
import org.deltafi.core.domain.generated.types.KeyValueInput;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.domain.generated.types.ObjectReferenceInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A specialization of the Result class which adds an object reference, and Metadata, which are produced be Transform, Load, and Format action types.
 */
public abstract class DataAmendedResult extends Result {
    protected ObjectReferenceInput objectReferenceInput;
    protected List<KeyValueInput> metadata = new ArrayList<>();

    public DataAmendedResult(ActionContext actionContext) {
        super(actionContext);
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

    public ObjectReferenceInput getObjectReference() {
        return objectReferenceInput;
    }

    public void setObjectReference(@NotNull ObjectReference objectReference) {
        objectReferenceInput = new ObjectReferenceInput.Builder()
                .name(objectReference.getName())
                .bucket(objectReference.getBucket())
                .offset(objectReference.getOffset())
                .size(objectReference.getSize())
                .build();
    }

    @SuppressWarnings("unused")
    public void setObjectReference(@NotNull String name, @NotNull String bucket, long size, long offset) {
        objectReferenceInput = new ObjectReferenceInput(name, bucket, offset, size);
    }
}
