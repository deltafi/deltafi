package org.deltafi.actionkit.action.format;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.ErrorDomain;
import org.deltafi.dgs.generated.types.ObjectReference;

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

    @Override
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.warn(params.getName() + " formatting (" + deltaFile.getDid() + ")");

        if (Objects.isNull(deltaFile.getDomain("error"))) {
            log.error("Error domain missing with did: {}", deltaFile.getDid());
            throw new RuntimeException("Error domain missing: " + deltaFile.getDid());
        }
        String json = deltaFile.getDomain("error");

        ObjectReference objectReference = contentService.putObject(json, deltaFile, params.getName());

        ErrorDomain errorDomain;
        try {
            errorDomain = objectMapper.readValue(json, ErrorDomain.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to ErrorDomain.\n" + json, e);
        }

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
