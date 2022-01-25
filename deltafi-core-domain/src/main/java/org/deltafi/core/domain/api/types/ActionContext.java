package org.deltafi.core.domain.api.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionContext {

    private String did;
    private String name;
    private String ingressFlow;
    private String egressFlow;
    private String hostname;
    private String actionVersion;

}
