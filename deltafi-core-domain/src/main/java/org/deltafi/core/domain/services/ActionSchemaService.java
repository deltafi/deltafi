package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    public DeleteActionSchema save(DeleteActionSchemaInput actionSchemaInput) {
        DeleteActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, DeleteActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public EgressActionSchema save(EgressActionSchemaInput actionSchemaInput) {
        EgressActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, EgressActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public EnrichActionSchema save(EnrichActionSchemaInput actionSchemaInput) {
        EnrichActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, EnrichActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public FormatActionSchema save(FormatActionSchemaInput actionSchemaInput) {
        FormatActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, FormatActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public LoadActionSchema save(LoadActionSchemaInput actionSchemaInput) {
        LoadActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, LoadActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public TransformActionSchema save(TransformActionSchemaInput actionSchemaInput) {
        TransformActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, TransformActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public ValidateActionSchema save(ValidateActionSchemaInput actionSchemaInput) {
        ValidateActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, ValidateActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }
}
