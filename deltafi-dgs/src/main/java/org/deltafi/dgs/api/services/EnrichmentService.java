package org.deltafi.dgs.api.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.api.repo.DeltaFileRepo;
import org.deltafi.dgs.api.types.DeltaFile;
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

