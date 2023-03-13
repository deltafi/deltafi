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
import org.deltafi.core.generated.types.ResumePolicyInput;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.ResumePolicyService;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.ResumePolicy;

import java.util.Collection;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class ResumePolicyDatafetcher {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ResumePolicyService resumePolicyService;

    @DgsQuery
    @NeedsPermission.ResumePolicyRead
    public Collection<ResumePolicy> getAllResumePolicies() {
        return resumePolicyService.getAll();
    }

    @DgsQuery
    @NeedsPermission.ResumePolicyRead
    public ResumePolicy getResumePolicy(@InputArgument String id) {
        return resumePolicyService.get(id).orElse(null);
    }

    @DgsMutation
    @NeedsPermission.ResumePolicyCreate
    public List<Result> loadResumePolicies(@InputArgument Boolean replaceAll, @InputArgument List<ResumePolicyInput> policies) {
        if (replaceAll) {
            resumePolicyService.removeAll();
        }
        return policies.stream().map(this::convertAndSave).toList();
    }

    @DgsMutation
    @NeedsPermission.ResumePolicyDelete
    public boolean removeResumePolicy(@InputArgument String id) {
        return resumePolicyService.remove(id);
    }

    @DgsMutation
    @NeedsPermission.ResumePolicyUpdate
    public Result updateResumePolicy(@InputArgument ResumePolicyInput resumePolicy) {
        ResumePolicy policy = OBJECT_MAPPER.convertValue(resumePolicy, ResumePolicy.class);
        return resumePolicyService.update(policy);
    }

    private Result convertAndSave(ResumePolicyInput resumePolicyInput) {
        ResumePolicy resumePolicy = OBJECT_MAPPER.convertValue(resumePolicyInput, ResumePolicy.class);
        return resumePolicyService.save(resumePolicy);
    }
}
