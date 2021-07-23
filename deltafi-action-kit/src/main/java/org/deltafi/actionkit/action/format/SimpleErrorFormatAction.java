package org.deltafi.actionkit.action.format;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.config.ObjectMapperConfig;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.client.GetErrorProjectionRoot;
import org.deltafi.dgs.generated.types.ErrorDomain;
import org.deltafi.dgs.generated.types.ObjectReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@SuppressWarnings("unused")
public class SimpleErrorFormatAction extends FormatAction<ActionParameters> {

    final private ContentService contentService;

    public SimpleErrorFormatAction() {
        super();
        contentService = ContentService.instance();
    }

    private final static ObjectMapper objectMapper =
            JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true)
                    .addModule(new JavaTimeModule()).build();

    private final static Map<String, BaseProjectionNode> projectionMap;

    static {
        Map<String, BaseProjectionNode> map = new HashMap<>();
        BaseProjectionNode bpn = new GetErrorProjectionRoot()
                .did()
                .cause()
                .context()
                .fromAction()
                .originatorDid()
                .originator()
                    .did()
                    .stage()
                    .sourceInfo()
                        .flow()
                        .filename()
                        .metadata().key().value().parent()
                    .parent()
                    .protocolStack()
                        .type()
                        .objectReference().name().size().offset().bucket().parent()
                        .metadata().key().value().parent()
                    .parent()
                    .actions()
                        .name()
// FIXME                       .created()
// FIXME                       .modified()
                        .state().parent()
                        .errorCause()
                        .errorContext()
                    .parent()
                    .formattedData()
                        .filename()
                        .formatAction()
                        .egressActions()
                        .objectReference().name().size().offset().bucket().parent()
                        .metadata().key().value().parent()
                    .parent()
                    .enrichment()
                        .enrichmentTypes()
                    .parent()
                    .domains()
                        .domainTypes()
                    .parent()
                    .markedForDelete()
                    .markedForDeleteReason()
// FIXME                   .created()
// FIXME                   .modified()
                .parent();
        // FIXME: For some reason, this constant isn't working...
        // map.put(DgsConstants.DELTAFIDOMAINS.Error, bpn);
        map.put("error", bpn);
        projectionMap = Collections.unmodifiableMap(map);

        ObjectMapperConfig omConfig = new ObjectMapperConfig();
        omConfig.customize(objectMapper);
    }

    @Override
    public Map<String, BaseProjectionNode> getDomainProjections() {
        return projectionMap;
    }

    @Override
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.warn(params.getName() + " formatting (" + deltaFile.getDid() + ")");

        if (Objects.isNull(deltaFile.getDomainDetails()) || !deltaFile.getDomainDetails().containsKey("error")) {
            log.error("Error domain missing with did: {}", deltaFile.getDid());
            throw new RuntimeException("Error domain missing: " + deltaFile.getDid());
        }
        JsonNode json = deltaFile.getDomainDetails().get("error");

        ErrorDomain errorDomain;
        try {
            errorDomain = objectMapper.convertValue(json, ErrorDomain.class);
        } catch (Throwable t) {
            throw new RuntimeException("Error converting json to ErrorDomain.\n" + json,t);
        }

        ObjectReference objectReference = contentService.putObject(json.toPrettyString(), deltaFile.getDid() + "_" + params.getName());

        String filename;
        try {
            filename = errorDomain.getOriginatorDid() +
                    "." + deltaFile.getSourceInfo().getFilename() +
                    ".error";
        } catch (Throwable t) {
            String err = "DeltaFile: Could not determine source file name for " + deltaFile.getDid();
            log.error(err, t);
            throw new RuntimeException(err, t);
        }

        FormatResult result = new FormatResult(params.getName(), deltaFile.getDid(), filename);
        addSourceInputMetadata(result, deltaFile);
        addProtocolStackMetadata(result, deltaFile);
        result.setObjectReference(objectReference);

        generateMetrics(deltaFile, params.getName());

        return result;
    }

    @Override
    public Class<ActionParameters> getParamType() {
        return ActionParameters.class;
    }
}
