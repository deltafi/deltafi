package org.deltafi.dgs.configuration;

import java.util.ArrayList;
import java.util.List;

public class IngressFlowConfiguration {
    private String type;
    // an ordered list of the TransformActions that each data item will flow through
    private List<String> transformActions = new ArrayList<>();
    // a list of candidate LoadActions that may operate on data in this flow
    private List<String> loadActions = new ArrayList<>();

    @SuppressWarnings("unused")
    public String getType() {
        return type;
    }

    @SuppressWarnings("unused")
    public void setType(String type) {
        this.type = type;
    }

    public List<String> getTransformActions() {
        return transformActions;
    }

    @SuppressWarnings("unused")
    public void setTransformActions(List<String> transformActions) {
        this.transformActions = transformActions;
    }

    public List<String> getLoadActions() {
        return loadActions;
    }

    @SuppressWarnings("unused")
    public void setLoadActions(List<String> loadActions) {
        this.loadActions = loadActions;
    }
}
