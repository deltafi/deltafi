package org.deltafi.dgs.configuration;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class DeletePolicyConfiguration {
    private String type;
    private Map<String, String> parameters = new HashMap<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
}
