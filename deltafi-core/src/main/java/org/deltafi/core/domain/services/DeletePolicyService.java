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
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.domain.repo.DeletePolicyRepo;
import org.deltafi.core.domain.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.domain.snapshot.Snapshotter;
import org.deltafi.core.domain.snapshot.SystemSnapshot;
import org.deltafi.core.domain.types.*;
import org.deltafi.core.domain.validation.DeletePolicyValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class DeletePolicyService implements Snapshotter {

    private final DeletePolicyRepo deletePolicyRepo;

    @PostConstruct
    public void indexCheck() {
        deletePolicyRepo.ensureAllIndices();
    }

    /**
     * Updates the enabled status of delete policy.
     *
     * @param id     id of the policy to update.
     * @param enable boolean to indicate if policy should be active.
     * @return true if updated, false if not found or enabled already set.
     */
    public boolean enablePolicy(String id, boolean enable) {
        Optional<DeletePolicy> policy = get(id);
        if (policy.isEmpty() || (policy.get().isLocked() && !enable) || (policy.get().isEnabled() == enable)) {
            return false;
        }
        policy.get().setEnabled(enable);
        deletePolicyRepo.save(policy.get());
        return true;
    }

    /**
     * Retrieve a policy by id.
     *
     * @param id id of the policy to find.
     * @return DeletePolicy if found.
     */
    public Optional<DeletePolicy> get(String id) {
        return deletePolicyRepo.findById(id);
    }

    /**
     * Get all delete policies.
     *
     * @return List of DeletePolicy
     */
    public List<DeletePolicy> getAll() {
        return deletePolicyRepo.findAll();
    }

    /**
     * Get only enabled delete policies.
     *
     * @return List of DeletePolicy
     */
    public List<DeletePolicy> getEnabledPolicies() {
        return deletePolicyRepo.findByEnabledIsTrue();
    }

    /**
     * Save one or more policies.
     *
     * @param replaceAll indicate if existing policies should be deleted
     * @param input      the policies and options to save
     * @return Result
     */
    public Result saveAll(boolean replaceAll, DeletePolicies input) {
        List<DeletePolicy> policies = input.allPolicies();

        List<String> errors = validate(policies);
        if (errors.isEmpty()) {
            if (replaceAll) {
                removeAll();
            }
            try {
                deletePolicyRepo.saveAll(policies);
                return new Result();
            } catch (DuplicateKeyException e) {
                errors.add("duplicate policy name");
            }
        }
        return Result.newBuilder().success(false).errors(errors).build();
    }

    /**
     * Set new delete properties for an existing policy.
     *
     * @param policy The new policy properties
     * @return Result
     */
    public Result update(DeletePolicy policy) {
        if (StringUtils.isBlank(policy.getId())) {
            return Result.newBuilder().success(false).errors(List.of("id is missing")).build();
        } else if (get(policy.getId()).isEmpty()) {
            return Result.newBuilder().success(false).errors(List.of("policy not found")).build();
        }

        List<String> errors = validate(List.of(policy));
        if (errors.isEmpty()) {
            try {
                deletePolicyRepo.save(policy);
                return new Result();
            } catch (DuplicateKeyException e) {
                errors.add("duplicate policy name");
            }
        }
        return Result.newBuilder().success(false).errors(errors).build();
    }

    /**
     * Delete a delete policy by id.
     *
     * @param id id of the policy to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(String id) {
        if (get(id).isPresent()) {
            deletePolicyRepo.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Remove all delete policies.
     */
    void removeAll() {
        deletePolicyRepo.deleteAll();
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        DeletePolicies deletePolicies = new DeletePolicies();
        List<DeletePolicy> allPolicies = deletePolicyRepo.findAll();

        for (DeletePolicy deletePolicy : allPolicies) {
            if (deletePolicy instanceof TimedDeletePolicy) {
                deletePolicies.getTimedPolicies().add((TimedDeletePolicy) deletePolicy);
            } else if (deletePolicy instanceof DiskSpaceDeletePolicy) {
                deletePolicies.getDiskSpacePolicies().add((DiskSpaceDeletePolicy) deletePolicy);
            } else {
                String type = null != deletePolicy ? deletePolicy.getClass().getName() : "null";
                throw new IllegalStateException("Delete Policy is not a known instance type: " + type);
            }
        }

        systemSnapshot.setDeletePolicies(deletePolicies);
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            deletePolicyRepo.deleteAll();
        }

        deletePolicyRepo.saveAll(systemSnapshot.getDeletePolicies().allPolicies());
        return Result.newBuilder().success(true).build();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.DELETE_POLICY_ORDER;
    }

    private List<String> validate(List<DeletePolicy> policies) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        policies.forEach(policy -> {
            if (policy.getId() == null) {
                policy.setId(UUID.randomUUID().toString());
            }
            errors.addAll(DeletePolicyValidator.validate(policy));
            String id = policy.getId();
            if (ids.contains(id)) {
                errors.add("duplicate policy id: " + id);
            } else {
                ids.add(id);
            }
        });
        return errors;
    }
}
