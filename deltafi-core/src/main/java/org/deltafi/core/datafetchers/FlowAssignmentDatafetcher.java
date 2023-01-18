/**
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
package org.deltafi.core.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.generated.types.FlowAssignmentRuleInput;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.FlowAssignmentService;
import org.deltafi.core.types.FlowAssignmentRule;
import org.deltafi.core.types.Result;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@DgsComponent
@RequiredArgsConstructor
public class FlowAssignmentDatafetcher {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final FlowAssignmentService flowAssignmentService;

    @DgsQuery
    @NeedsPermission.IngressRoutingRuleRead
    public Collection<FlowAssignmentRule> getAllFlowAssignmentRules() {
        return flowAssignmentService.getAll();
    }

    @DgsQuery
    @NeedsPermission.IngressRoutingRuleRead
    public FlowAssignmentRule getFlowAssignmentRule(@InputArgument String id) {
        return flowAssignmentService.get(id).orElse(null);
    }

    @DgsMutation
    @NeedsPermission.IngressRoutingRuleCreate
    public List<Result> loadFlowAssignmentRules(@InputArgument Boolean replaceAll, @InputArgument List<FlowAssignmentRuleInput> rules) {
        if (replaceAll) {
            flowAssignmentService.removeAll();
        }
        return rules.stream().map(this::convertAndSave).toList();
    }

    @DgsMutation
    @NeedsPermission.IngressRoutingRuleDelete
    public boolean removeFlowAssignmentRule(@InputArgument String id) {
        return flowAssignmentService.remove(id);
    }

    @DgsMutation
    @NeedsPermission.IngressRoutingRuleUpdate
    public Result updateFlowAssignmentRule(@InputArgument FlowAssignmentRuleInput rule) {
        FlowAssignmentRule flowAssignmentRule = OBJECT_MAPPER.convertValue(rule, FlowAssignmentRule.class);
        return flowAssignmentService.update(flowAssignmentRule);
    }

    private Result convertAndSave(FlowAssignmentRuleInput flowAssignmentRule) {
        FlowAssignmentRule rule = OBJECT_MAPPER.convertValue(flowAssignmentRule, FlowAssignmentRule.class);
        if (Objects.isNull(flowAssignmentRule.getPriority())) {
            rule.setPriority(FlowAssignmentService.DEFAULT_PRIORITY);
        }
        return flowAssignmentService.save(rule);
    }
}
