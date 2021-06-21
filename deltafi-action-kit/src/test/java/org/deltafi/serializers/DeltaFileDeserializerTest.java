package org.deltafi.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.config.ObjectMapperConfig;
import org.deltafi.types.DeltaFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaFileDeserializerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        ObjectMapperConfig omConfig = new ObjectMapperConfig();
        omConfig.customize(objectMapper);
    }

    @Test
    void testNoDomains() throws JsonProcessingException {
        String response = "{\"did\":\"977b7338-0298-4431-b930-18e8d08aca68\",\"enrichment\":{\"enrichmentTypes\":[]},\"protocolStack\":[{\"type\":\"xml-utf8\",\"objectReference\":{\"size\":2402,\"offset\":0,\"bucket\":\"incoming\",\"name\":\"52a1ffc6-dc5a-42a3-b156-3995027b643a\"},\"metadata\":[]},{\"type\":\"json-utf8-stix\",\"objectReference\":{\"size\":678,\"offset\":0,\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68\"},\"metadata\":[{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}]}],\"formattedData\":[{\"formatAction\":\"Stix2_1FormatAction\",\"metadata\":[{\"key\":\"sourceInfo.filename\",\"value\":\"indicator.xml\"},{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}],\"objectReference\":{\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68_Stix2_1FormatAction\",\"size\":336,\"offset\":0}}],\"sourceInfo\":{\"flow\":\"stix-up\",\"metadata\":[{\"key\":\"filename\",\"value\":\"indicator.xml\"}]}}";
        DeltaFile deltaFile = objectMapper.readValue(response, DeltaFile.class);

        assertNotNull(deltaFile);
        assertNull(deltaFile.getDomains());
        assertNull(deltaFile.getDomainDetails());

        assertEquals("977b7338-0298-4431-b930-18e8d08aca68", deltaFile.getDid());
        assertTrue(deltaFile.getEnrichment().getEnrichmentTypes().isEmpty());
        assertEquals(2, deltaFile.getProtocolStack().size());
        assertEquals(1, deltaFile.getFormattedData().size());
        assertEquals("Stix2_1FormatAction", deltaFile.getFormattedData().get(0).getFormatAction());

    }

    @Test
    void testDomainsWithoutObject() throws JsonProcessingException {
        String response = "{\"did\":\"977b7338-0298-4431-b930-18e8d08aca68\",\"domains\":{\"domainTypes\":[\"stix\"]},\"enrichment\":{\"enrichmentTypes\":[]},\"protocolStack\":[{\"type\":\"xml-utf8\",\"objectReference\":{\"size\":2402,\"offset\":0,\"bucket\":\"incoming\",\"name\":\"52a1ffc6-dc5a-42a3-b156-3995027b643a\"},\"metadata\":[]},{\"type\":\"json-utf8-stix\",\"objectReference\":{\"size\":678,\"offset\":0,\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68\"},\"metadata\":[{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}]}],\"formattedData\":[{\"formatAction\":\"Stix2_1FormatAction\",\"metadata\":[{\"key\":\"sourceInfo.filename\",\"value\":\"indicator.xml\"},{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}],\"objectReference\":{\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68_Stix2_1FormatAction\",\"size\":336,\"offset\":0}}],\"sourceInfo\":{\"flow\":\"stix-up\",\"metadata\":[{\"key\":\"filename\",\"value\":\"indicator.xml\"}]}}";
        DeltaFile deltaFile = objectMapper.readValue(response, DeltaFile.class);

        assertNotNull(deltaFile);
        assertNotNull(deltaFile.getDomains());
        assertEquals(1, deltaFile.getDomains().getDomainTypes().size());
        assertEquals("stix", deltaFile.getDomains().getDomainTypes().get(0));
        assertNull(deltaFile.getDomainDetails());
    }

    @Test
    void testOneDomain() throws JsonProcessingException {
        String response = "{\"did\":\"977b7338-0298-4431-b930-18e8d08aca68\",\"domains\":{\"domainTypes\":[\"stix\"],\"stix\":{\"__typename\":\"StixBundle\",\"id\":\"bundle--0935d61b-69a4-4e64-8c4c-d9ce885f7fcc\",\"type\":\"bundle\",\"objects\":[{\"__typename\":\"StixIndicator\",\"id\":\"indicator--ad560917-6ede-4abb-a4aa-994568a2abf4\",\"specVersion\":\"2.1\",\"type\":\"indicator\",\"createdByRef\":null,\"labels\":null,\"created\":\"2015-05-15T09:00:00Z\",\"modified\":\"2015-05-15T09:00:00Z\",\"revoked\":null,\"confidence\":null,\"lang\":null,\"externalReferences\":null,\"objectMarkingRefs\":null,\"granularMarkings\":null,\"extensions\":null,\"indicatorTypes\":[\"exfiltration\"],\"optionalName\":null,\"description\":\"Indicator that contains a SNORT signature.\",\"pattern\":\"log udp any any -> 192.168.1.0/24 1:1024\",\"patternType\":\"snort\",\"patternVersion\":null,\"validFrom\":\"2015-05-15T09:00:00Z\",\"validUntil\":null,\"killChainPhases\":null}]}},\"enrichment\":{\"enrichmentTypes\":[]},\"protocolStack\":[{\"type\":\"xml-utf8\",\"objectReference\":{\"size\":2402,\"offset\":0,\"bucket\":\"incoming\",\"name\":\"52a1ffc6-dc5a-42a3-b156-3995027b643a\"},\"metadata\":[]},{\"type\":\"json-utf8-stix\",\"objectReference\":{\"size\":678,\"offset\":0,\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68\"},\"metadata\":[{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}]}],\"formattedData\":[],\"sourceInfo\":{\"flow\":\"stix-up\",\"metadata\":[{\"key\":\"filename\",\"value\":\"indicator.xml\"}]}}";
        DeltaFile deltaFile = objectMapper.readValue(response, DeltaFile.class);

        assertNotNull(deltaFile);
        assertNotNull(deltaFile.getDomains());
        assertEquals(1, deltaFile.getDomainDetails().size());
        assertEquals("StixBundle", deltaFile.getDomainDetails().get("stix").get("__typename").asText());
    }

    @Test
    void testMultipleDomains() throws JsonProcessingException {
        String response = "{\"did\":\"977b7338-0298-4431-b930-18e8d08aca68\",\"domains\":{\"domainTypes\":[\"stix\", \"sample\"], \"stix\":{\"__typename\":\"StixBundle\",\"id\":\"bundle--0935d61b-69a4-4e64-8c4c-d9ce885f7fcc\",\"type\":\"bundle\",\"objects\":[{\"__typename\":\"StixIndicator\",\"id\":\"indicator--ad560917-6ede-4abb-a4aa-994568a2abf4\",\"specVersion\":\"2.1\",\"type\":\"indicator\",\"createdByRef\":null,\"labels\":null,\"created\":\"2015-05-15T09:00:00Z\",\"modified\":\"2015-05-15T09:00:00Z\",\"revoked\":null,\"confidence\":null,\"lang\":null,\"externalReferences\":null,\"objectMarkingRefs\":null,\"granularMarkings\":null,\"extensions\":null,\"indicatorTypes\":[\"exfiltration\"],\"optionalName\":null,\"description\":\"Indicator that contains a SNORT signature.\",\"pattern\":\"log udp any any -> 192.168.1.0/24 1:1024\",\"patternType\":\"snort\",\"patternVersion\":null,\"validFrom\":\"2015-05-15T09:00:00Z\",\"validUntil\":null,\"killChainPhases\":null}]}, \"sample\": {\"name\": \"sampleDomain\"}},\"enrichment\":{\"enrichmentTypes\":[]},\"protocolStack\":[{\"type\":\"xml-utf8\",\"objectReference\":{\"size\":2402,\"offset\":0,\"bucket\":\"incoming\",\"name\":\"52a1ffc6-dc5a-42a3-b156-3995027b643a\"},\"metadata\":[]},{\"type\":\"json-utf8-stix\",\"objectReference\":{\"size\":678,\"offset\":0,\"bucket\":\"storage\",\"name\":\"977b7338-0298-4431-b930-18e8d08aca68\"},\"metadata\":[{\"key\":\"stixType\",\"value\":\"bundle\"},{\"key\":\"stixVersion\",\"value\":\"2.1\"}]}],\"formattedData\":[],\"sourceInfo\":{\"flow\":\"stix-up\",\"metadata\":[{\"key\":\"filename\",\"value\":\"indicator.xml\"}]}}";

        DeltaFile deltaFile = objectMapper.readValue(response, DeltaFile.class);

        assertNotNull(deltaFile);
        assertNotNull(deltaFile.getDomains());
        assertEquals(2, deltaFile.getDomains().getDomainTypes().size());
        assertEquals(2, deltaFile.getDomainDetails().size());
        assertEquals("StixBundle", deltaFile.getDomainDetails().get("stix").get("__typename").asText());
        assertEquals("sampleDomain", deltaFile.getDomainDetails().get("sample").get("name").asText());
    }

}