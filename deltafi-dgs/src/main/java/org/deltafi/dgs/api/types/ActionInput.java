package org.deltafi.dgs.api.types;

import java.util.Map;

public class ActionInput {

    private DeltaFile deltaFile;
    private Map<String, Object> actionParams;

    public DeltaFile getDeltaFile() {
        return deltaFile;
    }

    public void setDeltaFile(DeltaFile deltaFile) {
        this.deltaFile = deltaFile;
    }

    public Map<String, Object> getActionParams() {
        return actionParams;
    }

    public void setActionParams(Map<String, Object> actionParams) {
        this.actionParams = actionParams;
    }

    @Override
    public String toString() {
        return "ActionInput{" +
                "deltaFile=" + deltaFile +
                ", actionParams=" + actionParams +
                '}';
    }
}
