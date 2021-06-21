package org.deltafi.dgs.api.datafetchers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.deltafi.dgs.generated.DgsConstants;
import org.deltafi.dgs.generated.types.DeltaFiDomains;

import java.util.Map;

public abstract class DomainsDatafetcher {
    final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @DgsEntityFetcher(name= DgsConstants.DELTAFIDOMAINS.TYPE_NAME)
    @SuppressWarnings("unused")
    public DeltaFiDomains deltaFiDomains(Map<String, Object> values) {
        String did = (String) values.get("did");
        DeltaFiDomains deltaFiDomains = new DeltaFiDomains();
        deltaFiDomains.setDid(did);
        return deltaFiDomains;
    }

    protected String getDid(DataFetchingEnvironment dfe) {
        DeltaFiDomains deltaFiDomains = objectMapper.convertValue(dfe.getSource(), DeltaFiDomains.class);
        return deltaFiDomains.getDid();
    }
}
