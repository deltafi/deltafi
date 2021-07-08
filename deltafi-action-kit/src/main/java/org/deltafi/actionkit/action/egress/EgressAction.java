package org.deltafi.actionkit.action.egress;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.config.DeltafiConfig;

import java.util.HashMap;
import java.util.Map;

@Slf4j
abstract public class EgressAction implements Action {

    protected String name;
    protected final Map<String, String> staticMetadata = new HashMap<>();

    @Override
    public void init(DeltafiConfig.ActionSpec spec) {
        name = spec.name;
        Action.addStaticMetadata(spec, staticMetadata, log);
    }

    @Override
    public String name() {
        return name;
    }

    public String flow() {
        String flowName = name();
        // this should always be true
        if (flowName.endsWith("EgressAction")) {
            flowName = flowName.substring(0, flowName.length() - 12);
        }
        char[] flowNameChars = flowName.toCharArray();
        flowNameChars[0] = Character.toLowerCase(flowNameChars[0]);
        return new String(flowNameChars);
    }
}