package org.deltafi.core.domain.api.types;

import lombok.Data;

import java.util.Map;

@Data
public class ActionInput {
    private DeltaFile deltaFile;
    private ActionContext actionContext;
    private Map<String, Object> actionParams;
    private String queueName;
}