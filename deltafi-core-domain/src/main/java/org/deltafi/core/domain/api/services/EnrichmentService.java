package org.deltafi.core.domain.api.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class EnrichmentService {

    final DeltaFileRepo deltaFileRepo;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public EnrichmentService(DeltaFileRepo deltaFileRepo) {
        this.deltaFileRepo = deltaFileRepo;
    }

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }

    @SuppressWarnings("unused")
    public DeltaFile addEnrichment(String did, String enrichment, String enrichmentJson)
    {
        DeltaFile deltaFile = getDeltaFile(did);

        if (Objects.isNull(deltaFile)) {
            throw new DgsEntityNotFoundException("Received " + enrichment + " enrichment for unknown did: " + did);
        }

        deltaFile.addEnrichment(enrichment, enrichmentJson);
        deltaFileRepo.save(deltaFile);

        return deltaFile;
    }
}