package org.deltafi.actionkit.action.egress;

public abstract class SimpleEgressAction extends EgressAction<EgressActionParameters> {
    public SimpleEgressAction() {
        super(EgressActionParameters.class);
    }
}
