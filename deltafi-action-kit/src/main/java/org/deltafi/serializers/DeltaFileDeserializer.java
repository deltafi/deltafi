package org.deltafi.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.deltafi.types.DeltaFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class DeltaFileDeserializer extends JsonDeserializer<DeltaFile> {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String DOMAINS = "domains";
    private static final List<String> KNOWN_DOMAIN_FIELDS = Arrays.asList("did", "domainTypes");

    @Override
    public DeltaFile deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        DeltaFile deltaFile = objectMapper.convertValue(node, DeltaFile.class);
        addDomains(deltaFile, node);

        return deltaFile;
    }

    private void addDomains(DeltaFile deltaFile, JsonNode deltafiNode) {
        if (deltafiNode.hasNonNull(DOMAINS)) {
            JsonNode domainNode = deltafiNode.get(DOMAINS);
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
}
