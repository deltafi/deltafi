/*
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

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeletePolicyService;
import org.deltafi.core.types.*;

import java.util.Collection;
import java.util.UUID;

@DgsComponent
@RequiredArgsConstructor
public class DeletePolicyDatafetcher {

    private final DeletePolicyService deletePolicyService;

    @DgsQuery
    @NeedsPermission.DeletePolicyRead
    public Collection<DeletePolicy> getDeletePolicies() {
        return deletePolicyService.getAll();
    }

    @DgsMutation
    @NeedsPermission.DeletePolicyCreate
    public Result loadDeletePolicies(@InputArgument Boolean replaceAll, @InputArgument DeletePolicies policies) {
        return deletePolicyService.saveAll(replaceAll, policies);
    }

    @DgsMutation
    @NeedsPermission.DeletePolicyDelete
    public boolean removeDeletePolicy(@InputArgument UUID id) {
        return deletePolicyService.remove(id);
    }

    @DgsMutation
    @NeedsPermission.DeletePolicyUpdate
    public boolean enablePolicy(@InputArgument UUID id, @InputArgument Boolean enabled) {
        return deletePolicyService.enablePolicy(id, enabled);
    }

    @DgsMutation
    @NeedsPermission.DeletePolicyUpdate
    public Result updateDiskSpaceDeletePolicy(@InputArgument DiskSpaceDeletePolicy policyUpdate) {
        return deletePolicyService.update(policyUpdate);
    }

    @DgsMutation
    @NeedsPermission.DeletePolicyUpdate
    public Result updateTimedDeletePolicy(@InputArgument TimedDeletePolicy policyUpdate) {
        return deletePolicyService.update(policyUpdate);
    }
}
