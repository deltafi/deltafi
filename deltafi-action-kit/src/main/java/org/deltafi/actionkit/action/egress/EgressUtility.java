package org.deltafi.actionkit.action.egress;

public class EgressUtility {

    // TODO - this should be a required field in the ActionInput for all Egress actions
    public static String flow(String actionName) {
        String flowName = actionName;
        // this should always be true
        if (flowName.endsWith("EgressAction")) {
            flowName = flowName.substring(0, flowName.length() - 12);
        }
        char[] flowNameChars = flowName.toCharArray();
        flowNameChars[0] = Character.toLowerCase(flowNameChars[0]);
        return new String(flowNameChars);
    }
}
