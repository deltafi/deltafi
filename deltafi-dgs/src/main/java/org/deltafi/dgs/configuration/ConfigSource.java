package org.deltafi.dgs.configuration;

public class ConfigSource {
    public enum Source {

        DEFAULT_FROM_PROPERTY, // merge configs from property file, user property version only if the config doesn't already exist
        OVERWRITE_FROM_PROPERTY, // merge configs from property, properties will overwrite configs that already exist
        RELOAD_FROM_PROPERTY,  // dump all existing configs and load settings from the properties, useful for testing
        EXTERNAL; // load configs from outside sources, nothing from properties will be loaded
    }

    private Source actions = Source.EXTERNAL;
    private Source flows = Source.EXTERNAL;

    public ConfigSource() {

    }

    public ConfigSource(Source actions, Source flows) {
        this.actions = actions;
        this.flows = flows;
    }

    public Source getActions() {
        return actions;
    }

    public void setActions(Source actions) {
        this.actions = actions;
    }

    public Source getFlows() {
        return flows;
    }

    public void setFlows(Source flows) {
        this.flows = flows;
    }
}
