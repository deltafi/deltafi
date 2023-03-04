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
import org.deltafi.core.generated.types.RetryPolicyInput;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.RetryPolicyService;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.RetryPolicy;

import java.util.Collection;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class RetryPolicyDatafetcher {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final RetryPolicyService retryPolicyService;

    @DgsQuery
    @NeedsPermission.RetryPolicyRead
    public Collection<RetryPolicy> getAllRetryPolicies() {
        return retryPolicyService.getAll();
    }

    @DgsQuery
    @NeedsPermission.RetryPolicyRead
    public RetryPolicy getRetryPolicy(@InputArgument String id) {
        return retryPolicyService.get(id).orElse(null);
    }

    @DgsMutation
    @NeedsPermission.RetryPolicyCreate
    public List<Result> loadRetryPolicies(@InputArgument Boolean replaceAll, @InputArgument List<RetryPolicyInput> policies) {
        if (replaceAll) {
            retryPolicyService.removeAll();
        }
        return policies.stream().map(this::convertAndSave).toList();
    }

    @DgsMutation
    @NeedsPermission.RetryPolicyDelete
    public boolean removeRetryPolicy(@InputArgument String id) {
        return retryPolicyService.remove(id);
    }

    @DgsMutation
    @NeedsPermission.RetryPolicyUpdate
    public Result updateRetryPolicy(@InputArgument RetryPolicyInput retryPolicy) {
        RetryPolicy policy = OBJECT_MAPPER.convertValue(retryPolicy, RetryPolicy.class);
        return retryPolicyService.update(policy);
    }

    private Result convertAndSave(RetryPolicyInput retryPolicyInput) {
        RetryPolicy retryPolicy = OBJECT_MAPPER.convertValue(retryPolicyInput, RetryPolicy.class);
        return retryPolicyService.save(retryPolicy);
    }
}
