package org.deltafi.dgs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.configuration.EgressActionConfiguration;
import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.configuration.LoadActionConfiguration;
import org.deltafi.dgs.configuration.TransformActionConfiguration;
import org.deltafi.dgs.configuration.ValidateActionConfiguration;
import org.deltafi.dgs.converters.KeyValueConverter;
import org.deltafi.dgs.exceptions.ActionConfigException;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.ActionConfigRepo;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ActionConfigService {

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ActionConfigRepo actionConfigRepo;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ActionConfigService(ActionConfigRepo actionConfigRepo) {
        this.actionConfigRepo = actionConfigRepo;
    }

    @Cacheable("actionConfig")
    public ActionConfiguration getConfigForAction(String actionName) throws ActionConfigException {
        List<ActionConfiguration> configs = actionConfigRepo.findByName(actionName);

        if (configs.isEmpty()) {
            throw new ActionConfigException(actionName, "Could not find action with the given name");
        } else if (configs.size() > 1) {
            throw new ActionConfigException(actionName, "Multiple actions found with the given name");
        }

        return configs.get(0);
    }

    @Cacheable("loadAction")
    public LoadActionConfiguration getLoadAction(String loadAction) {
        return actionConfigRepo.findLoadAction(loadAction);
    }

    @Cacheable("enrichAction")
    public EnrichActionConfiguration getEnrichAction(String enrichAction) {
        return actionConfigRepo.findEnrichAction(enrichAction);
    }

    @Cacheable("formatAction")
    public FormatActionConfiguration getFormatAction(String formatAction) {
        return actionConfigRepo.findFormatAction(formatAction);
    }

    public List<String> getEnrichActions() {
        return getActionNamesByType(ActionType.ENRICH_ACTION);
    }

    public List<String> getFormatActions() {
        return getActionNamesByType(ActionType.FORMAT_ACTION);
    }

    @Cacheable("actionConfigs")
    public List<ActionConfiguration> getActionConfigs(ActionQueryInput actionQueryInput) {
        if (Objects.nonNull(actionQueryInput)) {
            if (Objects.nonNull(actionQueryInput.getName())) {
                Optional<ActionConfiguration> actionConfiguration = actionConfigRepo.findByNameAndActionType(actionQueryInput.getName(), actionQueryInput.getActionType());
                return actionConfiguration.map(Collections::singletonList).orElse(Collections.emptyList());
            } else {
                return actionConfigRepo.findAllByActionType(actionQueryInput.getActionType());
            }
        }
        return actionConfigRepo.findAll();
    }

    @Cacheable("actionNames")
    public List<String> getActionNamesByType(ActionType actionType) {
        return actionConfigRepo.findAllByActionType(actionType).stream().map(ActionConfiguration::getName).collect(Collectors.toList());
    }

    public TransformActionConfiguration saveTransformAction(TransformActionConfigurationInput transformActionConfigurationInput) {
        return saveActionConfig(transformActionConfigurationInput, TransformActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public LoadActionConfiguration saveLoadAction(LoadActionConfigurationInput loadActionConfigurationInput) {
        LoadActionConfiguration fromInput = mapper.convertValue(loadActionConfigurationInput, LoadActionConfiguration.class);
        fromInput.setRequiresMetadata(KeyValueConverter.convertKeyValueInputs(loadActionConfigurationInput.getRequiresMetadataKeyValues()));
        return actionConfigRepo.upsertConfiguration(fromInput, LoadActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public EnrichActionConfiguration saveEnrichAction(EnrichActionConfigurationInput enrichActionConfigurationInput) {
        return saveActionConfig(enrichActionConfigurationInput, EnrichActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public FormatActionConfiguration saveFormatAction(FormatActionConfigurationInput formatActionConfigurationInput) {
        return saveActionConfig(formatActionConfigurationInput, FormatActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public ValidateActionConfiguration saveValidateAction(ValidateActionConfigurationInput validateActionConfigurationInput) {
        return saveActionConfig(validateActionConfigurationInput, ValidateActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public EgressActionConfiguration saveEgressAction(EgressActionConfigurationInput egressActionConfigurationInput) {
        return saveActionConfig(egressActionConfigurationInput, EgressActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public long removeActionConfigs(ActionQueryInput actionQueryInput) {
        if (Objects.nonNull(actionQueryInput)) {
            if (Objects.nonNull(actionQueryInput.getName())) {
                return actionConfigRepo.deleteByNameAndActionType(actionQueryInput.getName(), actionQueryInput.getActionType());
            } else {
                return actionConfigRepo.deleteAllByActionType(actionQueryInput.getActionType());
            }
        }
        return actionConfigRepo.deleteAllWithCount();
    }

    private <C extends ActionConfiguration> C saveActionConfig(Object input, Class<C> clazz) {
        C fromInput = mapper.convertValue(input, clazz);
        return actionConfigRepo.upsertConfiguration(fromInput, clazz);
    }

}
