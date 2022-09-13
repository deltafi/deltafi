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
package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.repo.FlowAssignmentRuleRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.FlowAssignmentRule;
import org.deltafi.core.types.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class FlowAssignmentService implements Snapshotter {

    public static final int DEFAULT_PRIORITY = 500;

    private final FlowAssignmentRuleRepo flowAssignmentRuleRepo;

    private List<FlowAssignmentRule> rulesCache = Collections.emptyList();

    @PostConstruct
    private void init() {
        refreshCache();
        indexCheck();
    }

    public void refreshCache() {
        rulesCache = getAll();
    }

    private void indexCheck() {
        flowAssignmentRuleRepo.ensureAllIndices();
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
     * Delete a flow assignment rule by id.
     *
     * @param id id of the rule to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(String id) {
        if (get(id).isPresent()) {
            flowAssignmentRuleRepo.deleteById(id);
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
     * Get a flow assignment rule by id.
     *
     * @param id id of the rule to return
     * @return flow assignment rule if found
     */
    public Optional<FlowAssignmentRule> get(String id) {
        return flowAssignmentRuleRepo.findById(id);
    }

    /**
     * Save/replace a flow assignment rule.
     *
     * @param flowAssignmentRule a rule for flow assignment
     * @return Result of operation
     */
    public Result save(FlowAssignmentRule flowAssignmentRule) {
        if (flowAssignmentRule.getId() == null) {
            flowAssignmentRule.setId(UUID.randomUUID().toString());
        }
        List<String> errors = flowAssignmentRule.validate();
        if (errors.isEmpty()) {
            try {
                flowAssignmentRuleRepo.save(flowAssignmentRule);
                refreshCache();
                return new Result();
            } catch (DuplicateKeyException e) {
                errors.add("duplicate rule name");
            }
        }
        return Result.newBuilder().success(false).errors(errors).build();
    }

    /**
     * Update a flow assignment rule.
     *
     * @param flowAssignmentRule the updated rule
     * @return Result of operation
     */
    public Result update(FlowAssignmentRule flowAssignmentRule) {
        if (StringUtils.isBlank(flowAssignmentRule.getId())) {
            return Result.newBuilder().success(false).errors(List.of("id is missing")).build();
        } else if (get(flowAssignmentRule.getId()).isEmpty()) {
            return Result.newBuilder().success(false).errors(List.of("rule not found")).build();
        }
        return save(flowAssignmentRule);
    }

    private Result saveAll(List<FlowAssignmentRule> flowAssignmentRules) {
        Result result = new Result();
        List<FlowAssignmentRule> valid = new ArrayList<>();
        for (FlowAssignmentRule flowAssignmentRule : flowAssignmentRules) {
            if (flowAssignmentRule.getId() == null) {
                flowAssignmentRule.setId(UUID.randomUUID().toString());
            }
            List<String> errors = flowAssignmentRule.validate();
            if (errors.isEmpty()) {
                valid.add(flowAssignmentRule);
            } else {
                result.setSuccess(false);
                result.getErrors().addAll(errors);
            }
        }

        if (!valid.isEmpty()) {
            try {
                flowAssignmentRuleRepo.saveAll(valid);
                refreshCache();
            } catch (DuplicateKeyException e) {
                result.getErrors().add("duplicate rule name");
            }
        }

        return result;
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

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setFlowAssignmentRules(flowAssignmentRuleRepo.findAll());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            flowAssignmentRuleRepo.deleteAll();
        }

        return saveAll(systemSnapshot.getFlowAssignmentRules());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.FLOW_ASSIGNMENT_ORDER;
    }
}
