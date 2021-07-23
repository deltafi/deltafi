package org.deltafi.dgs.services;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.converters.ErrorConverter;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ErrorService {
    private final DeltaFileRepo deltaFileRepo;

    public ErrorService(DeltaFileRepo deltaFileRepo) {
        this.deltaFileRepo = deltaFileRepo;
    }

    public ErrorDomain getError(String did) {
        DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
        if(deltaFile == null || deltaFile.getDomains() == null) return null;
        return ErrorConverter.convert(deltaFile.getDomains().getError());
    }

    public List<ErrorDomain> getErrorsFor(String did) {
        List<DeltaFile> deltaFiles = deltaFileRepo.findAllByDomainsErrorOriginatorDid(did);
        return deltaFiles.stream().map(err -> ErrorConverter.convert(err.getDomains().getError())).collect(Collectors.toList());
    }

}