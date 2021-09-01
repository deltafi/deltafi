package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.core.domain.generated.types.ActionSchemaInput;
import org.deltafi.core.domain.api.types.ActionSchemaImpl;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ActionSchemaService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActionSchemaRepo actionSchemaRepo;

    public ActionSchemaService(ActionSchemaRepo actionSchemaRepo) {
        this.actionSchemaRepo = actionSchemaRepo;
    }

    public ActionSchemaImpl save(ActionSchemaInput actionSchemaInput) {
        ActionSchemaImpl actionParamDefinition = objectMapper.convertValue(actionSchemaInput, ActionSchemaImpl.class);
        actionParamDefinition.setLastHeard(OffsetDateTime.now());
        return actionSchemaRepo.save(actionParamDefinition);
    }

    public List<ActionSchemaImpl> getAll() {
        return actionSchemaRepo.findAll();
    }

}