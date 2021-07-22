package org.deltafi.dgs.services;

import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.repo.ErrorRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ErrorService {
    private final ErrorRepo errorRepo;

    public ErrorService(ErrorRepo errorRepo) {
        this.errorRepo = errorRepo;
    }

    public ErrorDomain getError(String did) {
        return errorRepo.findById(did).orElse(null);
    }

    public List<ErrorDomain> getErrorsFor(String did) {
        return errorRepo.findAllByOriginatorDid(did);
    }

    public Boolean deleteError(String did) {
        return 1L == errorRepo.deleteByDid(did);
    }

    public List<Boolean> deleteErrors(List<String> dids) {
        return dids.stream().map(this::deleteError).collect(Collectors.toList());
    }
}