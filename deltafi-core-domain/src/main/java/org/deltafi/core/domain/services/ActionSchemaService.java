package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.types.EnrichActionSchema;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;
import org.deltafi.core.domain.generated.types.FormatActionSchema;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;
import org.deltafi.core.domain.generated.types.GenericActionSchema;
import org.deltafi.core.domain.generated.types.GenericActionSchemaInput;
import org.deltafi.core.domain.generated.types.LoadActionSchema;
import org.deltafi.core.domain.generated.types.LoadActionSchemaInput;
import org.deltafi.core.domain.generated.types.TransformActionSchema;
import org.deltafi.core.domain.generated.types.TransformActionSchemaInput;
import org.deltafi.core.domain.api.types.EnrichActionSchemaImpl;
import org.deltafi.core.domain.api.types.FormatActionSchemaImpl;
import org.deltafi.core.domain.api.types.GenericActionSchemaImpl;
import org.deltafi.core.domain.api.types.LoadActionSchemaImpl;
import org.deltafi.core.domain.api.types.TransformActionSchemaImpl;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.springframework.stereotype.Service;
import java.util.Optional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ActionSchemaService {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ActionSchemaRepo actionSchemaRepo;

    public ActionSchemaService(ActionSchemaRepo actionSchemaRepo) {
        this.actionSchemaRepo = actionSchemaRepo;
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

    public GenericActionSchema save(GenericActionSchemaInput actionSchemaInput) {
        GenericActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, GenericActionSchemaImpl.class);
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

    public List<ActionSchema> getAll() {
        return actionSchemaRepo.findAll();
    }

    public Optional<ActionSchema> getByActionClass(String id) {
        return actionSchemaRepo.findById(id);
    }
}
