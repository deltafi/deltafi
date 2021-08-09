package org.deltafi.dgs.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
                .domains(Collections.emptyList())
                .enrichment(Collections.emptyList())
                .formattedData(Collections.emptyList())
                .created(created)
                .modified(now)
                .build();
    }

    public static DeltaFile convert(DeltaFile originator, String errorDomain) {
        OffsetDateTime now = OffsetDateTime.now();
        return DeltaFile.newBuilder()
                .did(UUID.randomUUID().toString())
                .stage(DeltaFileStage.EGRESS.name())
                .actions(new ArrayList<>())
                .sourceInfo(originator.getSourceInfo())
                .protocolStack(Collections.emptyList())
                .domains(Collections.singletonList(KeyValue.newBuilder().key("error").value(errorDomain).build()))
                .enrichment(new ArrayList<>())
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