package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.api.types.DeleteActionSchema;
import org.deltafi.core.domain.api.types.EgressActionSchema;
import org.deltafi.core.domain.api.types.EnrichActionSchema;
import org.deltafi.core.domain.api.types.FormatActionSchema;
import org.deltafi.core.domain.api.types.LoadActionSchema;
import org.deltafi.core.domain.api.types.TransformActionSchema;
import org.deltafi.core.domain.api.types.ValidateActionSchema;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ActionSchemaService {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ActionSchemaRepo actionSchemaRepo;

    public ActionSchemaService(ActionSchemaRepo actionSchemaRepo) {
        this.actionSchemaRepo = actionSchemaRepo;
    }

    public List<ActionSchema> getAll() {
        return actionSchemaRepo.findAll();
    }

    public Optional<ActionSchema> getByActionClass(String id) {
        return actionSchemaRepo.findById(id);
    }

    public org.deltafi.core.domain.generated.types.DeleteActionSchema save(DeleteActionSchemaInput actionSchemaInput) {
        DeleteActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, DeleteActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.EgressActionSchema save(EgressActionSchemaInput actionSchemaInput) {
        EgressActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, EgressActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.EnrichActionSchema save(EnrichActionSchemaInput actionSchemaInput) {
        EnrichActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, EnrichActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.FormatActionSchema save(FormatActionSchemaInput actionSchemaInput) {
        FormatActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, FormatActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.LoadActionSchema save(LoadActionSchemaInput actionSchemaInput) {
        LoadActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, LoadActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.TransformActionSchema save(TransformActionSchemaInput actionSchemaInput) {
        TransformActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, TransformActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public org.deltafi.core.domain.generated.types.ValidateActionSchema save(ValidateActionSchemaInput actionSchemaInput) {
        ValidateActionSchema actionParamDefinition = objectMapper.convertValue(actionSchemaInput, ValidateActionSchema.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }
}
