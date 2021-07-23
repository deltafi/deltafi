package org.deltafi.passthrough.param;

import com.fasterxml.jackson.annotation.JsonSetter;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoteLoadParameters extends ActionParameters {

    private List<String> domains;

    public List<String> getDomains() {
        return domains;
    }

    @SuppressWarnings("unused")
    public void setDomains(List<String> domains) {
        this.domains = domains;
    }
}
