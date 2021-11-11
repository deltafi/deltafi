package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.EnrichInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EnrichResult extends Result {
    private final List<String> enrichments = new ArrayList<>();

    public EnrichResult(DeltaFile deltaFile, ActionParameters params) {
        super(deltaFile, params);
    }

    @SuppressWarnings("unused")
    public void addEnrichment(@NotNull String enrichment) {
        enrichments.add(enrichment);
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