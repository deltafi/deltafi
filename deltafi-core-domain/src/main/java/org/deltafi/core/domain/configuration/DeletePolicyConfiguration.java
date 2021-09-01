package org.deltafi.core.domain.configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class DeletePolicyConfiguration {
    private String type;
    private Map<String, String> parameters = new HashMap<>();
}