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
package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.services.DeletePolicyService;
import org.deltafi.core.domain.types.DeletePolicies;
import org.deltafi.core.domain.types.DeletePolicy;
import org.deltafi.core.domain.types.DiskSpaceDeletePolicy;
import org.deltafi.core.domain.types.Result;
import org.deltafi.core.domain.types.TimedDeletePolicy;

import java.util.Collection;

@DgsComponent
@RequiredArgsConstructor
public class DeletePolicyDatafetcher {

    private final DeletePolicyService deletePolicyService;

    @DgsQuery
    public Collection<DeletePolicy> getDeletePolicies() {
        return deletePolicyService.getAll();
    }

    @DgsMutation
    public Result loadDeletePolicies(boolean replaceAll, DeletePolicies policies) {
        return deletePolicyService.saveAll(replaceAll, policies);
    }

    @DgsMutation
    public boolean removeDeletePolicy(String id) {
        return deletePolicyService.remove(id);
    }

    @DgsMutation
    public boolean enablePolicy(String id, boolean enabled) {
        return deletePolicyService.enablePolicy(id, enabled);
    }

    @DgsMutation
    public Result updateDiskSpaceDeletePolicy(DiskSpaceDeletePolicy policyUpdate) {
        return deletePolicyService.update(policyUpdate);
    }

    @DgsMutation
    public Result updateTimedDeletePolicy(TimedDeletePolicy policyUpdate) {
        return deletePolicyService.update(policyUpdate);
    }
}
