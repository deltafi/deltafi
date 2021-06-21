package org.deltafi.dgs.services;

import org.deltafi.dgs.generated.types.SampleEnrichment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SampleEnrichmentsService {

    final Map<String, SampleEnrichment> sampleEnrichments = new HashMap<>();

    public Map<String, SampleEnrichment> getSampleEnrichments() {
        return sampleEnrichments;
    }

    public SampleEnrichment addSampleEnrichment(SampleEnrichment sampleEnrichment) {
        sampleEnrichments.put(sampleEnrichment.getDid(), sampleEnrichment);

        return sampleEnrichment;
    }

    public SampleEnrichment forDid(String did) {
        return sampleEnrichments.get(did);
    }
}
