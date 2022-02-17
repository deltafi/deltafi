package org.deltafi.actionkit.action.enrich;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.EnrichInput;
import org.deltafi.core.domain.generated.types.EnrichmentInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class EnrichResult extends Result {
    private final List<EnrichmentInput> enrichments = new ArrayList<>();

    public EnrichResult(ActionContext actionContext) {
        super(actionContext);
    }

    public void addEnrichment(@NotNull String enrichmentName, String value, String mediaType) {
        enrichments.add(new EnrichmentInput(enrichmentName, value, mediaType));
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.ENRICH;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setEnrich(EnrichInput.newBuilder().enrichments(enrichments).build());
        return event;
    }
}