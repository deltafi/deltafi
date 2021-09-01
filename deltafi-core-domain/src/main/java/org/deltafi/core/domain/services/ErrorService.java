package org.deltafi.core.domain.services;

import org.deltafi.core.domain.converters.ErrorConverter;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
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