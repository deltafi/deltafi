package org.deltafi.dgs.api.datafetchers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.deltafi.dgs.generated.DgsConstants;
import org.deltafi.dgs.generated.types.DeltaFiEnrichments;

import java.util.Map;

public abstract class EnrichmentsDatafetcher {
    final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @DgsEntityFetcher(name= DgsConstants.DELTAFIENRICHMENTS.TYPE_NAME)
    @SuppressWarnings("unused")
    public DeltaFiEnrichments deltaFiEnrichments(Map<String, Object> values) {
        String did = (String) values.get("did");
        DeltaFiEnrichments deltaFiEnrichments = new DeltaFiEnrichments();
        deltaFiEnrichments.setDid(did);
        return deltaFiEnrichments;
    }

    protected String getDid(DataFetchingEnvironment dfe) {
        DeltaFiEnrichments deltaFiEnrichments = objectMapper.convertValue(dfe.getSource(), DeltaFiEnrichments.class);
        return deltaFiEnrichments.getDid();
    }
}
