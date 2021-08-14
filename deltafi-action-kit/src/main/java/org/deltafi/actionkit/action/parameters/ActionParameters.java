package org.deltafi.actionkit.action.parameters;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ActionParameters {

    private String name;
    private Map<String, String> staticMetadata = new HashMap<>();

    public ActionParameters() {}

    public ActionParameters(String name, Map<String, String> staticMetadata) {
        this.name = name;
        if (Objects.nonNull(staticMetadata)) {
            this.staticMetadata = staticMetadata;
        }
    }

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
