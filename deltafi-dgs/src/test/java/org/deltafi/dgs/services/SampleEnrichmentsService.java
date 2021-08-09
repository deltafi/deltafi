package org.deltafi.dgs.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.dgs.api.services.EnrichmentService;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.SampleEnrichment;
import org.springframework.stereotype.Service;

@Service
public class SampleEnrichmentsService {

    final private EnrichmentService enrichmentService;
    final static ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("CdiInjectionPointsInspection")
    public SampleEnrichmentsService(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    public DeltaFile addSampleEnrichment(String did, SampleEnrichment sampleEnrichment) throws JsonProcessingException {
        return enrichmentService.addEnrichment(did, "sampleEnrichment", objectMapper.writeValueAsString(sampleEnrichment));
    }
}
