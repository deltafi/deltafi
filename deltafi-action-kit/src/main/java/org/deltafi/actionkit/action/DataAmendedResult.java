package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.KeyValue;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public abstract class DataAmendedResult extends Result {
    protected ContentReference contentReference;
    protected List<KeyValue> metadata = new ArrayList<>();

    public DataAmendedResult(ActionContext actionContext) {
        super(actionContext);
    }

    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }
}
