package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.deltafi.core.domain.generated.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SplitResult extends Result {
    List<SplitInput> splitInputs = new ArrayList<>();

    public SplitResult(ActionContext actionContext) {
        super(actionContext);
    }

    public void addChild(String filename, String flow, List<KeyValue> metadata, List<ContentInput> content) {
        splitInputs.add(SplitInput.newBuilder()
                .sourceInfo(new SourceInfo(filename, flow, metadata))
                .content(content)
                .build());
    }

    @SuppressWarnings("unused")
    public void addChild(String filename, String flow, Map<String, String> metadata, List<ContentInput> content) {
        addChild(filename, flow, KeyValueConverter.fromMap(metadata), content);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.SPLIT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setSplit(splitInputs);
        return event;
    }
}
