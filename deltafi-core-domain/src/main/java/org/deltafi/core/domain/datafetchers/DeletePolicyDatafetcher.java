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
import org.deltafi.core.domain.api.types.DeletePolicy;
import org.deltafi.core.domain.generated.types.LoadDeletePoliciesInput;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.DeletePolicyService;

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
    public Result loadDeletePolicies(boolean replaceAll, LoadDeletePoliciesInput policies) {
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

}
