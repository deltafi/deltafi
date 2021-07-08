package org.deltafi.actionkit.action.enrich;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.config.DeltafiConfig;

@Slf4j
abstract public class EnrichAction implements Action {

    protected String name;

    @Override
    public void init(DeltafiConfig.ActionSpec spec) {
        name = spec.name;
    }

    @Override
    public String name() {
        return name;
    }
}