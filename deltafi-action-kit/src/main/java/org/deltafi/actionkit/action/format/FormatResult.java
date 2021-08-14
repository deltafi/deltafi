package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FormatResult extends Result {

    public FormatResult(String name, String did, String filename) {
        super(name, did);
        this.filename = filename;
    }

    protected String filename;
    protected final List<KeyValueInput> metadata = new ArrayList<>();
    protected ObjectReferenceInput objectReferenceInput;

    private FormatInput formatInput() {
        return FormatInput.newBuilder()
                .filename(filename)
                .metadata(metadata)
                .objectReference(objectReferenceInput).build();
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

    public ObjectReferenceInput getObjectReference() {
        return objectReferenceInput;
    }

    @SuppressWarnings("unused")
    public void setObjectReference(@NotNull String name, @NotNull String bucket, long size, long offset) {
        objectReferenceInput = new ObjectReferenceInput(name, bucket, offset, size);
    }

    public String getFilename() { return filename; }

    @SuppressWarnings("unused")
    public void setFilename(String filename) { this.filename = filename; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.FORMAT; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFormat(formatInput());
        return event;
    }
}