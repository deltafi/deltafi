package org.deltafi.passthrough.param;


import org.deltafi.actionkit.action.parameters.ActionParameters;

public class RoteTransformParameters extends ActionParameters {

    private String resultType;

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
}
