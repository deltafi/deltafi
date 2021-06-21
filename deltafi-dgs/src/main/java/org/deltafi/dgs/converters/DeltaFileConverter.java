package org.deltafi.dgs.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.*;

import java.time.OffsetDateTime;
import java.util.*;

public class DeltaFileConverter {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private DeltaFileConverter() {}

    public static DeltaFile convert(SourceInfoInput sourceInfoInput, ObjectReferenceInput objectReferenceInput, String fileType) {
        String did = UUID.randomUUID().toString();
        ProtocolLayer protocolLayer = ProtocolLayer.newBuilder().type(fileType).objectReference(mapper.convertValue(objectReferenceInput, ObjectReference.class)).metadata(new ArrayList<>()).build();
        OffsetDateTime now = OffsetDateTime.now();
        return DeltaFile.newBuilder()
                .did(did)
                .stage(DeltaFileStage.INGRESS.name())
                .actions(new ArrayList<>())
                .sourceInfo(mapper.convertValue(sourceInfoInput, SourceInfo.class))
                .protocolStack(new ArrayList<>(Collections.singletonList(protocolLayer)))
                .domains(DeltaFiDomains.newBuilder().did(did).domainTypes(new ArrayList<>()).build())
                .enrichment(DeltaFiEnrichments.newBuilder().did(did).enrichmentTypes(new ArrayList<>()).build())
                .formattedData(new ArrayList<>())
                .created(now)
                .modified(now)
                .build();
    }

    public static List<KeyValue> convertKeyValueInputs(List<KeyValueInput> keyValueInputs) {
        return mapper.convertValue(keyValueInputs, new TypeReference<>(){});
    }

    public static ProtocolLayer convert(ProtocolLayerInput input) {
        return ProtocolLayer.newBuilder()
                .objectReference(mapper.convertValue(input.getObjectReference(), ObjectReference.class))
                .type(input.getType())
                .metadata(convertKeyValueInputs(input.getMetadata()))
                .build();
    }

    public static Map<String, String> convertKeyValues(List<KeyValue> keyValues) {
        Map<String, String> keyValueMap = new HashMap<>();
        for (KeyValue keyValue : keyValues) {
            keyValueMap.put(keyValue.getKey(), keyValue.getValue());
        }

        return keyValueMap;
    }

    public static ObjectReference convert(ObjectReferenceInput input) {
        return mapper.convertValue(input, ObjectReference.class);
    }
}
