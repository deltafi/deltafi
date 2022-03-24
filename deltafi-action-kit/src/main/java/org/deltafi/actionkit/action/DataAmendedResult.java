package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.generated.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public abstract class DataAmendedResult extends Result {
    protected List<Content> content = new ArrayList<>();
    protected List<KeyValue> metadata = new ArrayList<>();

    public DataAmendedResult(ActionContext context) {
        super(context);
    }

    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }

    @SuppressWarnings("unused")
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }

    @SuppressWarnings("unused")
    public void addContent(@NotNull Content contentItem) {
        content.add(contentItem);
    }

    @SuppressWarnings("unused")
    public void addContentReference(@NotNull ContentReference contentReference) {
        addContent(Content.newBuilder().contentReference(contentReference).build());
    }
}
