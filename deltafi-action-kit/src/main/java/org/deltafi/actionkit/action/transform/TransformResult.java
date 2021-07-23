package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransformResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public TransformResult(String name, String did) {
        super(name, did);
    }

    protected final List<KeyValueInput> metadata = new ArrayList<>();
    protected ObjectReferenceInput objectReferenceInput;
    protected String type;

    private TransformInput transformInput() {
        return TransformInput.newBuilder()
                .protocolLayer(
                        new ProtocolLayerInput.Builder()
                                .metadata(metadata)
                                .type(type)
                                .objectReference(objectReferenceInput)
                                .build())
                .build();
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

    @SuppressWarnings("unused")
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

    @Override
    final public ResultType resultType() { return ResultType.QUEUE; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.TRANSFORM; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setTransform(transformInput());
        return event;
    }
}