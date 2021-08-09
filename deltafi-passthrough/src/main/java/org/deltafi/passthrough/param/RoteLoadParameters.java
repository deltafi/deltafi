package org.deltafi.passthrough.param;

import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;

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
