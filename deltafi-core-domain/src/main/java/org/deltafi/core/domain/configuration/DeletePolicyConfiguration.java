package org.deltafi.core.domain.configuration;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class DeletePolicyConfiguration {
    private String type;
    private Map<String, String> parameters = new HashMap<>();
}