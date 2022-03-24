package org.deltafi.actionkit.action.format;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FormatInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class FormatResult extends Result {
    private final String filename;
    protected ContentReference contentReference;
    protected List<KeyValue> metadata = new ArrayList<>();

    public FormatResult(@NotNull ActionContext context, @NotNull String filename) {
        super(context);
        this.filename = filename;
    }

    @SuppressWarnings("unused")
    public void addMetadata(KeyValue keyValue) {
        metadata.add(keyValue);
    }

    public void addMetadata(KeyValue keyValue, String prefix) {
        metadata.add(new KeyValue(prefix + keyValue.getKey(), keyValue.getValue()));
    }

    public void addMetadata(List<KeyValue> keyValues) {
        metadata.addAll(keyValues);
    }

    public void addMetadata(List<KeyValue> keyValues, String prefix) {
        keyValues.forEach(kv -> addMetadata(kv, prefix));
    }

    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }

    @SuppressWarnings("unused")
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.FORMAT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFormat(FormatInput.newBuilder()
                .filename(filename)
                .contentReference(contentReference)
                .metadata(metadata)
                .build());
        return event;
    }
}
