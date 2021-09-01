package org.deltafi.core.domain.api.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DomainsService {

    final DeltaFileRepo deltaFileRepo;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DomainsService(DeltaFileRepo deltaFileRepo) {
        this.deltaFileRepo = deltaFileRepo;
    }

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }

    @SuppressWarnings("unused")
    public DeltaFile addDomain(String did, String domain, String domainJson)
    {
        DeltaFile deltaFile = getDeltaFile(did);

        if (Objects.isNull(deltaFile)) {
            throw new DgsEntityNotFoundException("Received " + domain + " domain for unknown did: " + did);
        }

        deltaFile.addDomain(domain, domainJson);
        deltaFileRepo.save(deltaFile);

        return deltaFile;
    }
}