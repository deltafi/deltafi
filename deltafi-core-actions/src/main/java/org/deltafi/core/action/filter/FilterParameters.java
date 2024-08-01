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
package org.deltafi.core.action.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;

import static org.deltafi.core.action.filter.FilterBehavior.ANY;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class FilterParameters extends ActionParameters {
    @JsonPropertyDescription("A list of Spring Expression Language (SpEL) expressions used to filter")
    private List<String> filterExpressions;

    @JsonProperty(defaultValue = "ANY")
    @JsonPropertyDescription("Specifies the filter behavior. 'ANY' will filter if any expression matches. 'ALL' will filter if all expressions match. 'NONE' will filter if no expressions match. Defaults to ANY.")
    private FilterBehavior filterBehavior = ANY;
}
