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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.DeletePolicy;
import org.deltafi.core.domain.api.types.DiskSpaceDeletePolicy;
import org.deltafi.core.domain.api.types.TimedDeletePolicy;
import org.deltafi.core.domain.generated.types.DiskSpaceDeletePolicyInput;
import org.deltafi.core.domain.generated.types.LoadDeletePoliciesInput;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.generated.types.TimedDeletePolicyInput;
import org.deltafi.core.domain.repo.DeletePolicyRepo;
import org.deltafi.core.domain.validation.DeletePolicyValidator;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class DeletePolicyService {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DeletePolicyRepo deletePolicyRepo;

    /**
     * Updates the enabled status of delete policy.
     *
     * @param id     id of the policy to update.
     * @param enable boolean to indicate if policy should be active.
     * @return true if updated, false if not found or enabled already set.
     */
    public boolean enablePolicy(String id, boolean enable) {
        Optional<DeletePolicy> policy = get(id);
        if (!policy.isPresent() || (policy.get().getLocked() && !enable) || (policy.get().getEnabled() == enable)) {
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
    public Result saveAll(boolean replaceAll, LoadDeletePoliciesInput input) {
        List<DeletePolicy> policies = new ArrayList<>();
        input.getTimedPolicies().forEach(t -> policies.add(convert(t)));
        input.getDiskSpacePolicies().forEach(d -> policies.add(convert(d)));

        List<String> errors = validate(policies);
        if (errors.isEmpty()) {
            if (replaceAll) {
                removeAll();
            }
            deletePolicyRepo.saveAll(policies);
            return new Result(true, List.of());
        }

        return new Result(false, errors);
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


    private org.deltafi.core.domain.generated.types.DiskSpaceDeletePolicy convert(DiskSpaceDeletePolicyInput inputPolicy) {
        return objectMapper.convertValue(inputPolicy, DiskSpaceDeletePolicy.class);
    }

    private org.deltafi.core.domain.generated.types.TimedDeletePolicy convert(TimedDeletePolicyInput inputPolicy) {
        return objectMapper.convertValue(inputPolicy, TimedDeletePolicy.class);
    }

    private List<String> validate(List<DeletePolicy> policies) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        policies.stream().forEach(policy -> {
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
