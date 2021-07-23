package org.deltafi.actionkit.action.parameters;

import java.util.HashMap;
import java.util.Map;

public class ActionParameters {

    private String name;
    private Map<String, String> staticMetadata = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getStaticMetadata() {
        return staticMetadata;
    }

    public void setStaticMetadata(Map<String, String> staticMetadata) {
        this.staticMetadata = staticMetadata;
    }
}
