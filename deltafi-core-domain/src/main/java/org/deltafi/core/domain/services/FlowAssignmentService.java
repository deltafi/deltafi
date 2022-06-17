/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.FlowAssignmentRule;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.repo.FlowAssignmentRuleRepo;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
public class FlowAssignmentService {

    public static final int DEFAULT_PRIORITY = 500;

    private final FlowAssignmentRuleRepo flowAssignmentRuleRepo;

    private List<FlowAssignmentRule> rulesCache = Collections.emptyList();

    @PostConstruct
    public void refreshCache() {
        rulesCache = getAll();
    }

    /**
     * Get all flow assignment rules.
     *
     * @return List of FlowAssignmentRule
     */
    public List<FlowAssignmentRule> getAll() {
        return flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc();
    }

    /**
     * Delete a flow assignment rule by name.
     *
     * @param name name of the rule to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(String name) {
        if (get(name).isPresent()) {
            flowAssignmentRuleRepo.deleteById(name);
            refreshCache();
            return true;
        }
        return false;
    }

    /**
     * Remove all flow assignment rules from the DB.
     */
    public void removeAll() {
        flowAssignmentRuleRepo.deleteAll();
        refreshCache();

    }

    /**
     * Get a flow assignment rule by name.
     *
     * @param name name of the rule to return
     * @return flow assignment rule if found
     */
    public Optional<FlowAssignmentRule> get(String name) {
        return flowAssignmentRuleRepo.findById(name);
    }

    /**
     * Save/replace a flow assignment rule.
     *
     * @param flowAssignmentRule a rule for flow assignment
     * @return Result of operation
     */
    public Result save(FlowAssignmentRule flowAssignmentRule) {
        List<String> errors = flowAssignmentRule.validate();
        if (errors.isEmpty()) {
            flowAssignmentRuleRepo.save(flowAssignmentRule);
            refreshCache();
            return new Result(true, List.of());
        }
        return new Result(false, errors);
    }

    /**
     * Iterate through all rules to find a match for the given SourceInfo.
     *
     * @param sourceInfo input source info to match
     * @return flow name if found, else null
     */
    public String findFlow(SourceInfo sourceInfo) {
        final List<FlowAssignmentRule> rules = rulesCache;
        for (FlowAssignmentRule rule : rules) {
            if (rule.matches(sourceInfo)) {
                return rule.getFlow();
            }
        }
        return null;
    }
}
