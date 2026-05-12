/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.ResumePolicyRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class ResumePolicyService implements Snapshotter {

    private final ResumePolicyRepo resumePolicyRepo;
    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
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
     * Find an enabled resume policy matching the specified criteria.
     * Disabled policies are skipped here. Returns empty when auto-resume is globally
     * disabled so new errors are not marked for resume while the system-wide toggle is off
     * (matching the behavior of disabling every individual policy).
     *
     * @param attempt    number of times action attempted
     * @param errorCause error cause text.
     * @param flowName   the sourceInfo dataSource.
     * @param actionName name of action with error.
     * @return Optional ResumePolicy if found, else empty.
     */
    Optional<ResumePolicy> find(int attempt, String errorCause, String flowName, String actionName) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isAutoResumeEnabled()) {
            return Optional.empty();
        }
        for (ResumePolicy policy : policiesCache) {
            if (Boolean.TRUE.equals(policy.getEnabled()) && policy.isMatch(attempt, errorCause, flowName, actionName)) {
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
     * Set the resume policy enabled field to the given value
     * @param id of the resume policy to change the enable setting
     * @param enable new enabled value
     * @return true if the value was changed for the policy. If the policy
     * was not found this returns false.
     */
    public boolean enablePolicy(UUID id, boolean enable) {
        ResumePolicy policy = get(id).orElse(null);
        if (policy == null || policy.getEnabled() == enable) {
            return false;
        }
        policy.setEnabled(enable);
        resumePolicyRepo.save(policy);
        refreshCache();
        log.info("{} resume policy {}", enable ? "Enabled" : "Disabled", policy.getName());
        return true;
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
                action.getName());

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
        return resumePolicyRepo.findByOrderByPriorityAndName();
    }

    /**
     * Save/replace a resume policy.
     *
     * @param resumePolicy resume policy to save
     * @return Result of operation
     */
    public Result save(ResumePolicy resumePolicy) {
        applyEnabledDefault(resumePolicy);
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
     * Delete a resume policy by id and clear any in-flight auto-resume schedules stamped with
     * its name in the same transaction. Affected DeltaFiles fall to the standard "errored,
     * no schedule" state and need to be resumed manually.
     *
     * @param id id of the policy to delete.
     * @return true if deleted; false if not found
     */
    @Transactional
    public boolean remove(UUID id) {
        ResumePolicy policy = get(id).orElse(null);
        if (policy == null) {
            return false;
        }
        int cleared = deltaFileRepo.clearAutoResumeSchedulesByReason(policy.getName());
        resumePolicyRepo.deleteById(id);
        refreshCache();
        if (cleared > 0) {
            log.info("Deleted resume policy {} and cleared {} in-flight schedule(s)",
                    policy.getName(), cleared);
        }
        return true;
    }

    /**
     * Remove all resume policies and clear every in-flight auto-resume schedule in the same
     * transaction.
     */
    @Transactional
    public void removeAll() {
        int cleared = deltaFileRepo.clearAllAutoResumeSchedules();
        resumePolicyRepo.deleteAll();
        refreshCache();
        log.info("Deleted all resume policies and cleared {} in-flight schedule(s)", cleared);
    }

    private Result saveAll(List<ResumePolicy> policies) {
        Result result = new Result();
        List<ResumePolicy> valid = new ArrayList<>();
        for (ResumePolicy policy : policies) {
            applyEnabledDefault(policy);
            if (policy.getId() == null) {
                policy.setId(Generators.timeBasedEpochGenerator().generate());
            }
            List<String> errors = policy.validate();
            if (errors.isEmpty()) {
                valid.add(policy);
            } else {
                result.getInfo().addAll(errors);
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
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setResumePolicies(resumePolicyRepo.findAll());
    }

    @Override
    @SuppressWarnings({"java:S6809", "java:S2229", "SpringTransactionalMethodCallsInspection"})
    // reset is inside its own transaction, the removeAll transaction isn't needed
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (hardReset) {
            removeAll();
        }

        return saveAll(snapshot.getResumePolicies());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.RESUME_POLICY_ORDER;
    }

    // Pre-V77 snapshots and JSON exports omit the enabled field, and Jackson's
    // convertValue path explicitly nulls it out on input. Default to enabled true.
    private void applyEnabledDefault(ResumePolicy policy) {
        if (policy.getEnabled() == null) {
            policy.setEnabled(true);
        }
    }

    public record ResumeDetails(String name, Integer delay) {
    }
}
