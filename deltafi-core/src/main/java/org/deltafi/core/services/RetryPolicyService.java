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
package org.deltafi.core.services;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.Action;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.repo.RetryPolicyRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
//public class RetryPolicyService implements Snapshotter {
public class RetryPolicyService {

    private final RetryPolicyRepo retryPolicyRepo;

    private List<RetryPolicy> policiesCache;

    @PostConstruct
    private void init() {
        retryPolicyRepo.ensureAllIndices();
        refreshCache();
    }

    public void refreshCache() {
        policiesCache = getAll();
    }

    /**
     * Find a retry policy for the specified criteria.
     *
     * @param errorCause error cause text.
     * @param flow       flow where error occurred.
     * @param action     name of action with error.
     * @param actionType type of action with error.
     * @return Optional RetryPolicy if found, else empty.
     */
    Optional<RetryPolicy> find(String errorCause, String flow, String action, String actionType) {
        for (RetryPolicy policy : policiesCache) {
            if (policy.isMatch(errorCause, flow, action, actionType)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    /**
     * Search for a retry policy matching the action error, and apply retry policy if found and appropriate.
     *
     * @param deltaFile  - The DeltaFile being acted upon
     * @param event      - The error event
     * @param actionType - The action's type
     * @return An Optional of the delay for the next execution
     */
    public Optional<Integer> getRetryDelay(DeltaFile deltaFile, ActionEventInput event, String actionType) {
        Optional<RetryPolicy> policy = find(
                event.getError().getCause(),
                deltaFile.getSourceInfo().getFlow(),
                event.getAction(),
                actionType);
        if (!policy.isEmpty()) {
            Optional<Action> action = deltaFile.actionNamed(event.getAction());
            if (!action.isEmpty() && action.get().getAttempt() < policy.get().getMaxAttempts()) {
                return Optional.of(computeDelay(policy.get().getBackOff(), action.get().getAttempt()));
            }
        }

        return Optional.empty();
    }

    int computeDelay(BackOff backOff, int attempt) {
        int delay = backOff.getDelay();
        boolean randomDelay = null != backOff.getRandom() && backOff.getRandom().booleanValue();
        if (randomDelay) {
            delay = new Random().nextInt(delay, 1 + backOff.getMaxDelay());
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
     * @return RetryPolicy if found.
     */
    public Optional<RetryPolicy> get(String id) {
        return retryPolicyRepo.findById(id);
    }

    /**
     * Get all retry policies.
     *
     * @return List of RetryPolicy
     */
    public List<RetryPolicy> getAll() {
        return retryPolicyRepo.findAll();
    }

    /**
     * Save/replace a retry policy.
     *
     * @param retryPolicy retry policy to save
     * @return Result of operation
     */
    public Result save(RetryPolicy retryPolicy) {
        if (retryPolicy.getId() == null) {
            retryPolicy.setId(UUID.randomUUID().toString());
        }
        List<String> errors = retryPolicy.validate();
        if (errors.isEmpty()) {
            try {
                retryPolicyRepo.save(retryPolicy);
                refreshCache();
                return new Result();
            } catch (DuplicateKeyException e) {
                errors.add("duplicate match criteria");
            }
        }
        return Result.newBuilder().success(false).errors(errors).build();
    }

    /**
     * Set new properties for an existing retry policy.
     *
     * @param policy The new policy properties
     * @return Result
     */
    public Result update(RetryPolicy policy) {
        if (StringUtils.isBlank(policy.getId())) {
            return Result.newBuilder().success(false).errors(List.of("id is missing")).build();
        } else if (get(policy.getId()).isEmpty()) {
            return Result.newBuilder().success(false).errors(List.of("policy not found")).build();
        }
        return save(policy);
    }

    /**
     * Delete a retry policy by id.
     *
     * @param id id of the policy to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(String id) {
        if (get(id).isPresent()) {
            retryPolicyRepo.deleteById(id);
            refreshCache();
            return true;
        }
        return false;
    }

    /**
     * Remove all retry policies.
     */
    public void removeAll() {
        retryPolicyRepo.deleteAll();
        refreshCache();
    }

    /*
    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        RetryPolicies deletePolicies = new RetryPolicies();
        List<RetryPolicy> allPolicies = retryPolicyRepo.findAll();

        for (RetryPolicy deletePolicy : allPolicies) {
            if (deletePolicy instanceof TimedRetryPolicy) {
                deletePolicies.getTimedPolicies().add((TimedRetryPolicy) deletePolicy);
            } else if (deletePolicy instanceof DiskSpaceRetryPolicy) {
                deletePolicies.getDiskSpacePolicies().add((DiskSpaceRetryPolicy) deletePolicy);
            } else {
                String type = null != deletePolicy ? deletePolicy.getClass().getName() : "null";
                throw new IllegalStateException("Retry Policy is not a known instance type: " + type);
            }
        }

        systemSnapshot.setRetryPolicies(deletePolicies);
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            retryPolicyRepo.deleteAll();
        }

        retryPolicyRepo.saveAll(systemSnapshot.getRetryPolicies().allPolicies());
        return Result.newBuilder().success(true).build();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.RETRY_POLICY_ORDER;
    }
    */
}
