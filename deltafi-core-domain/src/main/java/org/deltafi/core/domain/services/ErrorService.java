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

import org.deltafi.core.domain.converters.ErrorConverter;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorService {
    private final DeltaFileRepo deltaFileRepo;

    public ErrorService(DeltaFileRepo deltaFileRepo) {
        this.deltaFileRepo = deltaFileRepo;
    }

    public ErrorDomain getError(String did) {
        DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
        if(deltaFile == null || deltaFile.getDomains() == null) return null;
        return ErrorConverter.convert(deltaFile.getDomain("error"));
    }

    public List<ErrorDomain> getErrorsFor(String did) {
        // TODO: once we have child/parent relationships between deltaFiles, query that way
        return null;
        //List<DeltaFile> deltaFiles = deltaFileRepo.findAllByDomainsErrorOriginatorDid(did);
        //return deltaFiles.stream().map(err -> ErrorConverter.convert(err.getDomains().get("error"))).collect(Collectors.toList());
    }

}