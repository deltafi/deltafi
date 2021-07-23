package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ActionEventType;
import org.deltafi.dgs.generated.types.EnrichInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EnrichResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public EnrichResult(String name, String did) {
        super(name, did);
    }

    protected final List<String> enrichments = new ArrayList<>();

    private EnrichInput enrichInput() {
        return EnrichInput.newBuilder()
                .enrichments(enrichments)
                .build();
    }

    @SuppressWarnings("unused")
    public void addEnrichment(@NotNull String enrichment) {
        enrichments.add(enrichment);
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.ENRICH; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setEnrich(enrichInput());
        return event;
    }
}