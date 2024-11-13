/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.uuid.Generators;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.Action;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.repo.ResumePolicyRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.ResumePolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class ResumePolicyService implements Snapshotter {

    private final ResumePolicyRepo resumePolicyRepo;
    private final Random random = new Random();
    private List<ResumePolicy> policiesCache;

    @PostConstruct
    private void init() {
        refreshCache();
    }

    public void refreshCache() {
        policiesCache = getAll();
    }

    /**
     * Find a resume policy for the specified criteria.
     *
     * @param attempt    number of times action attempted
     * @param errorCause error cause text.
     * @param flowName       the sourceInfo dataSource.
     * @param actionName     name of action with error.
     * @param actionType type of action with error.
     * @return Optional ResumePolicy if found, else empty.
     */
    Optional<ResumePolicy> find(int attempt, String errorCause, String flowName, String actionName, ActionType actionType) {
        for (ResumePolicy policy : policiesCache) {
            if (policy.isMatch(attempt, errorCause, flowName, actionName, actionType)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    Optional<ResumePolicy> findByName(String name) {
        return policiesCache.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    /**
     * Compare the last action with ERROR of the DeltaFile
     * for a match against the resume policy.
     *
     * @param policy    the resume policy
     * @param deltaFile the DeltaFile
     * @return true if a match; otherwise false
     */
    boolean matchesActionError(ResumePolicy policy, DeltaFile deltaFile) {
        return deltaFile.erroredFlows().stream()
                .anyMatch(erroredFlow -> matchesActionError(policy, deltaFile, erroredFlow));
    }

    private boolean matchesActionError(ResumePolicy policy, DeltaFile deltaFile, DeltaFileFlow deltaFileFlow) {
        Action lastAction = deltaFileFlow.lastAction();
        return lastAction != null && policy.isMatch(lastAction, deltaFile.getDataSource());
    }

    /**
     * Determine if the named resume policy can be applied to a list of DeltaFiles
     * and return the ones that match.
     *
     * @param policy      The policy to use for evaluation
     * @param deltaFiles  A list of DeltaFiles to be checked against the named policy
     * @param excludeDids A set of DeltaFile dids to ignore
     * @return List of DeltaFiles that can be resumed
     */
    public List<DeltaFile> canBeApplied(ResumePolicy policy, List<DeltaFile> deltaFiles, Set<UUID> excludeDids) {
        return deltaFiles.stream()
                .filter(d -> !excludeDids.contains(d.getDid()))
                .filter(d -> matchesActionError(policy, d))
                .toList();
    }

    /**
     * Search for a resume policy matching the action error, and apply resume policy if found and appropriate.
     *
     * @param deltaFile  - The DeltaFile being acted upon
     * @param action     - The errored action
     * @param errorCause - The error cause
     * @return An Optional of the delay details for the next execution
     */
    public Optional<ResumeDetails> getAutoResumeDelay(DeltaFile deltaFile, Action action, String errorCause) {
        Optional<ResumePolicy> policy = find(
                action.getAttempt(),
                errorCause,
                deltaFile.getDataSource(),
                action.getName(),
                action.getType());

        return policy.map(resumePolicy -> new ResumeDetails(resumePolicy.getName(),
                computeDelay(resumePolicy.getBackOff(), action.getAttempt())));
    }

    int computeDelay(BackOff backOff, int attempt) {
        int delay = backOff.getDelay();
        boolean randomDelay = null != backOff.getRandom() && backOff.getRandom();
        if (randomDelay) {
            delay = random.nextInt(delay, 1 + backOff.getMaxDelay());
        } else if (null != backOff.getMultiplier()) {
            delay = attempt * backOff.getMultiplier() * delay;
            if (null != backOff.getMaxDelay() && delay > backOff.getMaxDelay()) {
                delay = backOff.getMaxDelay();
            }
        }
        return delay;
    }

    /**
     * Retrieve a policy by id.
     *
     * @param id id of the policy to find.
     * @return ResumePolicy if found.
     */
    public Optional<ResumePolicy> get(UUID id) {
        return resumePolicyRepo.findById(id);
    }

    /**
     * Get all resume policies.
     *
     * @return List of ResumePolicy
     */
    public List<ResumePolicy> getAll() {
        return resumePolicyRepo.findByOrderByPriorityDesc();
    }

    /**
     * Save/replace a resume policy.
     *
     * @param resumePolicy resume policy to save
     * @return Result of operation
     */
    public Result save(ResumePolicy resumePolicy) {
        if (resumePolicy.getId() == null) {
            resumePolicy.setId(Generators.timeBasedEpochGenerator().generate());
        }
        List<String> errors = resumePolicy.validate();
        if (errors.isEmpty()) {
            try {
                resumePolicyRepo.save(resumePolicy);
                refreshCache();
                return new Result();
            } catch (DataIntegrityViolationException e) {
                errors.add("duplicate name or criteria");
            }
        }
        return Result.builder().success(false).errors(errors).build();
    }

    /**
     * Set new properties for an existing resume policy.
     *
     * @param policy The new policy properties
     * @return Result
     */
    public Result update(ResumePolicy policy) {
        if (policy.getId() == null) {
            return Result.builder().success(false).errors(List.of("id is missing")).build();
        } else if (get(policy.getId()).isEmpty()) {
            return Result.builder().success(false).errors(List.of("policy not found")).build();
        }

        return save(policy);
    }

    /**
     * Delete a resume policy by id.
     *
     * @param id id of the policy to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(UUID id) {
        if (get(id).isPresent()) {
            resumePolicyRepo.deleteById(id);
            refreshCache();
            return true;
        }
        return false;
    }

    /**
     * Remove all resume policies.
     */
    public void removeAll() {
        resumePolicyRepo.deleteAll();
        refreshCache();
    }

    private Result saveAll(List<ResumePolicy> policies) {
        Result result = new Result();
        List<ResumePolicy> valid = new ArrayList<>();
        for (ResumePolicy policy : policies) {
            if (policy.getId() == null) {
                policy.setId(Generators.timeBasedEpochGenerator().generate());
            }
            List<String> errors = policy.validate();
            if (errors.isEmpty()) {
                valid.add(policy);
            } else {
                result.setSuccess(false);
                result.getErrors().addAll(errors);
            }
        }

        if (!valid.isEmpty()) {
            try {
                resumePolicyRepo.saveAll(valid);
                refreshCache();
            } catch (DataIntegrityViolationException e) {
                result.getErrors().add("duplicate name or criteria");
            }
        }
        return result;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setResumePolicies(resumePolicyRepo.findAll());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            removeAll();
        }

        return saveAll(systemSnapshot.getResumePolicies());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.RESUME_POLICY_ORDER;
    }

    public record ResumeDetails(String name, Integer delay) {
    }
}
