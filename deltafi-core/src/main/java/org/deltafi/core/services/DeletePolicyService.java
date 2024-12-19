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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.*;
import org.deltafi.core.repo.DeletePolicyRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.validation.DeletePolicyValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class DeletePolicyService implements Snapshotter {

    private final DeletePolicyRepo deletePolicyRepo;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public static final String TTL_SYSTEM_POLICY = "ttlSystemPolicy";

    /**
     * Updates the enabled status of delete policy.
     *
     * @param id     id of the policy to update.
     * @param enable boolean to indicate if policy should be active.
     * @return true if updated, false if not found or enabled already set.
     */
    public boolean enablePolicy(UUID id, boolean enable) {
        Optional<DeletePolicy> policy = get(id);
        if (policy.isEmpty() || policy.get().isEnabled() == enable) {
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
    public Optional<DeletePolicy> get(UUID id) {
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

    private DeletePolicy ttlDeletePolicy() {
        return TimedDeletePolicy.builder()
                .deleteMetadata(true)
                .name(TTL_SYSTEM_POLICY)
                .enabled(true)
                .afterCreate("P" + deltaFiPropertiesService.getDeltaFiProperties().getAgeOffDays() + "D")
                .build();
    }

    /**
     * Get only enabled delete policies.
     *
     * @return List of DeletePolicy
     */
    public List<DeletePolicy> getEnabledPolicies() {
        List<DeletePolicy> deletePolicies = deletePolicyRepo.findByEnabledIsTrue();
        deletePolicies.add(ttlDeletePolicy());
        return deletePolicies;
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
            } catch (DataIntegrityViolationException e) {
                errors.add("duplicate policy name");
            }
        }
        return Result.builder().success(false).errors(errors).build();
    }

    /**
     * Set new delete properties for an existing policy.
     *
     * @param policy The new policy properties
     * @return Result
     */
    public Result update(DeletePolicy policy) {
        if (policy.getId() == null) {
            return Result.builder().success(false).errors(List.of("id is missing")).build();
        }

        List<String> errors = validate(List.of(policy));
        if (!errors.isEmpty()) {
            return Result.builder().success(false).errors(errors).build();
        }

        Optional<DeletePolicy> existingPolicyOpt = get(policy.getId());
        if (existingPolicyOpt.isEmpty()) {
            return Result.builder().success(false).errors(List.of("policy not found")).build();
        }

        DeletePolicy existingPolicy = existingPolicyOpt.get();

        if (!existingPolicy.getClass().equals(policy.getClass())) {
            // Types don't match, we need to delete the old one and insert the new one
            deletePolicyRepo.delete(existingPolicy);
            deletePolicyRepo.save(policy);
            return Result.builder()
                    .success(true)
                    .info(List.of("Policy type changed and was replaced"))
                    .build();
        } else {
            // Types match, we can update the existing policy
            // Copy non-null properties from the new policy to the existing one
            BeanUtils.copyProperties(policy, existingPolicy, getNullPropertyNames(policy));
            try {
                deletePolicyRepo.save(existingPolicy);
                return Result.builder().success(true).build();
            } catch (DataIntegrityViolationException e) {
                errors.add("duplicate policy name");
                return Result.builder().success(false).errors(errors).build();
            }
        }
    }

    private String[] getNullPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) emptyNames.add(pd.getName());
        }

        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    /**
     * Delete a delete policy by id.
     *
     * @param id id of the policy to delete.
     * @return true if deleted; false if not found
     */
    public boolean remove(UUID id) {
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
    public void updateSnapshot(Snapshot snapshot) {
        DeletePolicies deletePolicies = new DeletePolicies();
        List<DeletePolicy> allPolicies = deletePolicyRepo.findAll();

        for (DeletePolicy deletePolicy : allPolicies) {
            if (deletePolicy instanceof TimedDeletePolicy timedDeletePolicy) {
                deletePolicies.getTimedPolicies().add(timedDeletePolicy);
            } else if (deletePolicy instanceof DiskSpaceDeletePolicy diskSpaceDeletePolicy) {
                deletePolicies.getDiskSpacePolicies().add(diskSpaceDeletePolicy);
            } else {
                String type = null != deletePolicy ? deletePolicy.getClass().getName() : "null";
                throw new IllegalStateException("Delete Policy is not a known instance type: " + type);
            }
        }

        snapshot.setDeletePolicies(deletePolicies);
    }

    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (hardReset) {
            deletePolicyRepo.deleteAll();
        }

        deletePolicyRepo.saveAll(snapshot.getDeletePolicies().allPolicies());
        return Result.builder().success(true).build();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.DELETE_POLICY_ORDER;
    }

    private List<String> validate(List<DeletePolicy> policies) {
        List<String> errors = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        policies.forEach(policy -> {
            if (policy.getId() == null) {
                policy.setId(Generators.timeBasedEpochGenerator().generate());
            }
            errors.addAll(DeletePolicyValidator.validate(policy));
            UUID id = policy.getId();
            if (ids.contains(id)) {
                errors.add("duplicate policy id: " + id);
            } else {
                ids.add(id);
            }
        });
        return errors;
    }
}
