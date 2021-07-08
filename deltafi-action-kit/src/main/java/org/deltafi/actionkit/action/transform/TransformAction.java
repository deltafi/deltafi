package org.deltafi.actionkit.action.transform;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.config.DeltafiConfig;

import java.util.HashMap;
import java.util.Map;

@Slf4j
abstract public class TransformAction implements Action {

    protected String name;
    protected final Map<String,String> staticMetadata = new HashMap<>();

    @Override
    public void init(DeltafiConfig.ActionSpec spec) {
        name = spec.name;
        Action.addStaticMetadata(spec, staticMetadata, log);
    }

    @Override
    public String name() {
        return name;
    }
}