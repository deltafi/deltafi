package org.deltafi.actionkit.action;

import lombok.Data;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public abstract class Result {
    protected final DeltaFile deltaFile;
    protected final ActionParameters params;

    protected ObjectReferenceInput objectReferenceInput;
    protected List<KeyValueInput> metadata = new ArrayList<>();

    public abstract ActionEventType actionEventType();

    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action(params.getName())
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
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