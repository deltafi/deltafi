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
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.ErrorDomain;
import org.deltafi.core.domain.services.ErrorService;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class ErrorDatafetcher {
    private final ErrorService errorService;

    @DgsQuery
    @SuppressWarnings("unused")
    public ErrorDomain getError(String did) {
        ErrorDomain errorDomain = errorService.getError(did);

        if (errorDomain == null) {
            throw new DgsEntityNotFoundException("ErrorDomain " + did + " not found.");
        }

        return errorDomain;
    }

    @DgsQuery
    @SuppressWarnings("unused")
    public List<ErrorDomain> getErrorsFor(String originatorDid) {
        return errorService.getErrorsFor(originatorDid);
    }
}