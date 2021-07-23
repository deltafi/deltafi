package org.deltafi.dgs.api.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.deltafi.dgs.api.types.DeltaFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class DeltaFileDeserializer extends JsonDeserializer<DeltaFile> {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String DOMAINS = "domains";
    private static final List<String> KNOWN_DOMAIN_FIELDS = Arrays.asList("did", "domainTypes");

    private static final String ENRICHMENT = "enrichment";
    private static final List<String> KNOWN_ENRICHMENT_FIELDS = Arrays.asList("did", "enrichmentTypes");

    @Override
    public DeltaFile deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        DeltaFile deltaFile = objectMapper.convertValue(node, DeltaFile.class);
        addDomains(deltaFile, node);
        addEnrichment(deltaFile, node);

        return deltaFile;
    }

    private void addDomains(DeltaFile deltaFile, JsonNode deltaFileNode) {
        if (deltaFileNode.hasNonNull(DOMAINS)) {
            JsonNode domainNode = deltaFileNode.get(DOMAINS);
            Iterator<String> fieldnames = domainNode.fieldNames();
            while (fieldnames.hasNext()) {
                String fieldName = fieldnames.next();
                if (unknownDomainField(fieldName)) {
                    deltaFile.addDomainDetails(fieldName, domainNode.get(fieldName));
                }
            }
        }
    }

    private boolean unknownDomainField(String key) {
        return !KNOWN_DOMAIN_FIELDS.contains(key);
    }

    private void addEnrichment(DeltaFile deltaFile, JsonNode deltaFileNode) {
        if (deltaFileNode.hasNonNull(ENRICHMENT)) {
            JsonNode enrichmentNode = deltaFileNode.get(ENRICHMENT);
            Iterator<String> fieldnames = enrichmentNode.fieldNames();
            while (fieldnames.hasNext()) {
                String fieldName = fieldnames.next();
                if (unknownEnrichmentField(fieldName)) {
                    deltaFile.addEnrichmentDetails(fieldName, enrichmentNode.get(fieldName));
                }
            }
        }
    }

    private boolean unknownEnrichmentField(String key) {
        return !KNOWN_ENRICHMENT_FIELDS.contains(key);
    }
}
