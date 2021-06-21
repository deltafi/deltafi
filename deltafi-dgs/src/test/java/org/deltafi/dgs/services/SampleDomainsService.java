package org.deltafi.dgs.services;

import org.deltafi.dgs.generated.types.SampleDomain;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SampleDomainsService {

    final Map<String, SampleDomain> sampleDomains = new HashMap<>();

    public Map<String, SampleDomain> getSampleDomains() {
        return sampleDomains;
    }

    public SampleDomain addSampleDomain(SampleDomain sampleEnrichment) {
        sampleDomains.put(sampleEnrichment.getDid(), sampleEnrichment);

        return sampleEnrichment;
    }

    public SampleDomain forDid(String did) {
        return sampleDomains.get(did);
    }
}
