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
import java.util.ArrayList;
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

    public void removeAllInList(List<String> actionNames) {
        actionSchemaRepo.deleteAllById(actionNames);
    }

    public int saveAll(ActionRegistrationInput input) {
        List<ActionSchema> savedRegistrations = new ArrayList<>();

        if (input.getTransformActions() != null) {
            input.getTransformActions().forEach(a -> savedRegistrations.add(convert(a)));

        }
        if (input.getLoadActions() != null) {
            input.getLoadActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        if (input.getEnrichActions() != null) {
            input.getEnrichActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        if (input.getFormatActions() != null) {
            input.getFormatActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        if (input.getValidateActions() != null) {
            input.getValidateActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        if (input.getEgressActions() != null) {
            input.getEgressActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        if (input.getDeleteActions() != null) {
            input.getDeleteActions().forEach(a -> savedRegistrations.add(convert(a)));
        }

        actionSchemaRepo.saveAll(savedRegistrations);

        return savedRegistrations.size();
    }

    private org.deltafi.core.domain.generated.types.DeleteActionSchema convert(DeleteActionSchemaInput actionSchemaInput) {
        DeleteActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, DeleteActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.EgressActionSchema convert(EgressActionSchemaInput actionSchemaInput) {
        EgressActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, EgressActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.EnrichActionSchema convert(EnrichActionSchemaInput actionSchemaInput) {
        EnrichActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, EnrichActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.FormatActionSchema convert(FormatActionSchemaInput actionSchemaInput) {
        FormatActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, FormatActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.LoadActionSchema convert(LoadActionSchemaInput actionSchemaInput) {
        LoadActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, LoadActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.TransformActionSchema convert(TransformActionSchemaInput actionSchemaInput) {
        TransformActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, TransformActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

    private org.deltafi.core.domain.generated.types.ValidateActionSchema convert(ValidateActionSchemaInput actionSchemaInput) {
        ValidateActionSchema actionSchema = objectMapper.convertValue(actionSchemaInput, ValidateActionSchema.class);
        actionSchema.setLastHeard(OffsetDateTime.now());
        return actionSchema;
    }

}
