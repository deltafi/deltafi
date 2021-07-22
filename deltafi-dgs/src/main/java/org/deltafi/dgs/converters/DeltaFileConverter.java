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

    public static DeltaFile convert(String did, SourceInfoInput sourceInfoInput, ObjectReferenceInput objectReferenceInput, OffsetDateTime created, String fileType) {
        ProtocolLayer protocolLayer = ProtocolLayer.newBuilder().type(fileType).objectReference(mapper.convertValue(objectReferenceInput, ObjectReference.class)).metadata(new ArrayList<>()).build();
        OffsetDateTime now = OffsetDateTime.now();
        return DeltaFile.newBuilder()
                .did(did)
                .stage(DeltaFileStage.INGRESS.name())
                .actions(new ArrayList<>())
                .sourceInfo(mapper.convertValue(sourceInfoInput, SourceInfo.class))
                .protocolStack(Collections.singletonList(protocolLayer))
                .domains(DeltaFiDomains.newBuilder().did(did).domainTypes(Collections.emptyList()).build())
                .enrichment(DeltaFiEnrichments.newBuilder().did(did).enrichmentTypes(Collections.emptyList()).build())
                .formattedData(Collections.emptyList())
                .created(created)
                .modified(now)
                .build();
    }

    public static DeltaFile convert(DeltaFile originator, ErrorDomain errorDomain) {
        OffsetDateTime now = OffsetDateTime.now();
        String did = errorDomain.getDid();
        return DeltaFile.newBuilder()
                .did(did)
                .stage(DeltaFileStage.LOAD.name())
                .actions(new ArrayList<>())
                .sourceInfo(originator.getSourceInfo())
                .protocolStack(Collections.emptyList())
                .domains(DeltaFiDomains.newBuilder()
                        .did(did)
                        .domainTypes(Collections.singletonList("error"))
                        .error(errorDomain)
                        .build())
                .enrichment(DeltaFiEnrichments.newBuilder()
                        .did(did)
                        .enrichmentTypes(Collections.emptyList())
                        .build())
                .formattedData(Collections.emptyList())
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

    public static ObjectReference convert(ObjectReferenceInput input) {
        return mapper.convertValue(input, ObjectReference.class);
    }
}