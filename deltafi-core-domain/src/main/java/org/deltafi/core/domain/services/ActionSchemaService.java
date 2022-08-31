/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.common.types.ActionRegistrationInput;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.plugin.Plugin;
import org.deltafi.core.domain.plugin.PluginCleaner;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.deltafi.core.domain.types.*;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ActionSchemaService implements PluginCleaner {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ActionSchemaRepo actionSchemaRepo;
    private final DeltaFiProperties deltaFiProperties;

    public ActionSchemaService(ActionSchemaRepo actionSchemaRepo, DeltaFiProperties deltaFiProperties) {
        this.actionSchemaRepo = actionSchemaRepo;
        this.deltaFiProperties = deltaFiProperties;
    }

    public List<ActionSchema> getAll() {
        return actionSchemaRepo.findAll();
    }

    public boolean verifyActionsExist(List<String> actionNames) {
        OffsetDateTime threshold = OffsetDateTime.now().minus(deltaFiProperties.getActionInactivityThreshold());
        return actionNames.size() == actionSchemaRepo.countAllByIdInAndLastHeardGreaterThanEqual(actionNames, threshold);
    }

    public Optional<ActionSchema> getByActionClass(String id) {
        return actionSchemaRepo.findById(id);
    }

    public int saveAll(ActionRegistrationInput input) {
        List<ActionSchema> savedRegistrations = new ArrayList<>();

        addSchemas(savedRegistrations, input.getTransformActions(), TransformActionSchema.class);
        addSchemas(savedRegistrations, input.getLoadActions(), LoadActionSchema.class);
        addSchemas(savedRegistrations, input.getDomainActions(), DomainActionSchema.class);
        addSchemas(savedRegistrations, input.getEnrichActions(), EnrichActionSchema.class);
        addSchemas(savedRegistrations, input.getFormatActions(), FormatActionSchema.class);
        addSchemas(savedRegistrations, input.getValidateActions(), ValidateActionSchema.class);
        addSchemas(savedRegistrations, input.getEgressActions(), EgressActionSchema.class);

        return actionSchemaRepo.saveAll(savedRegistrations).size();
    }

    /**
     * Convert the list of ActionSchema inputs to the given ActionSchema type
     * and add them to the schemaList
     * @param schemaList holds the list of schemas that need to be saved
     * @param inputs ActionSchema input objects that need to be converted
     * @param type the type of ActionSchema to convert the input to
     * @param <T> the type of ActionSchema to convert the input to
     */
    private <T extends ActionSchema> void addSchemas(List<ActionSchema> schemaList, List<?> inputs, Class<T> type) {
        if (null != inputs) {
            inputs.stream().map(input -> convert(input, type)).forEach(schemaList::add);
        }
    }

    /**
     * Convert the ActionSchema input object to the given schemaClass. Sets the lastHeard
     * time to the current time.
     * @param inputObject schema input to convert
     * @param schemaClass the type of ActionSchema that the input should be mapped to
     * @param <T> the type of ActionSchema that the input should be mapped to
     * @return the converted ActionSchema
     */
    private <T extends ActionSchema> T convert(Object inputObject, Class<T> schemaClass) {
        T schema = objectMapper.convertValue(inputObject, schemaClass);
        schema.setLastHeard(OffsetDateTime.now());
        return schema;
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        actionSchemaRepo.deleteAllById(plugin.actionNames());
    }
}
