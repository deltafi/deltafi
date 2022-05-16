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

import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.converters.ErrorConverter;
import org.deltafi.core.domain.generated.types.DeltaFilesFilter;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ErrorService {
    private final DeltaFileRepo deltaFileRepo;

    public ErrorDomain getError(String did) {
        Optional<DeltaFile> deltaFile = deltaFileRepo.findById(did);
        if (deltaFile.isEmpty()) {
            return null;
        }
        return ErrorConverter.convert(deltaFile.get().getDomain(Constants.ERROR_DOMAIN));
    }

    /**
     * Gets the ErrorDomain instances for each error triggered processing the DeltaFile of the given id. The resulting
     * list is ordered by creation date in descending order (most recent first).
     * @param did the id of the DeltaFile
     * @return a list of ErrorDomain instances for the DeltaFile, which will be empty if there are no errors
     */
    public List<ErrorDomain> getErrorsFor(String did) {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDomains(List.of(Constants.ERROR_DOMAIN));
        filter.setParentDid(did);

        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, Integer.MAX_VALUE, filter, null);

        return deltaFiles.getDeltaFiles().stream()
                .map(deltaFile -> ErrorConverter.convert(deltaFile.getDomain(Constants.ERROR_DOMAIN)))
                .collect(Collectors.toList());
    }
}