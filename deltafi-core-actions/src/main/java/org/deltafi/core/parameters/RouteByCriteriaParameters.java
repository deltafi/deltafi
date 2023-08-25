/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.HashMap;
import java.util.Map;

import static org.deltafi.core.parameters.NoMatchBehavior.ERROR;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class RouteByCriteriaParameters extends ActionParameters {
    @JsonPropertyDescription("""
            A map of SpEL expressions to flow names. If an expression evaluates to true, the content will be routed to the corresponding flow. Examples:
            - To route to 'flow1' if metadata key 'x' is 'y': {"metadata['x'] == 'y'": 'flow1'}"
            - To route to 'flow2' if 'x' is not present: {"!metadata.containsKey('x')": 'flow2'}
            """)
    @JsonProperty(required = true)
    private Map<String, String> routingExpressions = new HashMap<>();

    @JsonPropertyDescription("Defines the behavior when no routing expressions match. Can be one of 'ERROR', 'FILTER', or 'PASSTHROUGH'.")
    private NoMatchBehavior noMatchBehavior = ERROR;
}
