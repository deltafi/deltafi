/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.validation;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.services.PluginService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ActionConfigurationValidator {

    private final PluginService pluginService;

    public ActionConfigurationValidator(@Lazy PluginService pluginService) {
        this.pluginService = pluginService;
    }

    public List<FlowConfigError> validate(ActionConfiguration actionConfiguration) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (StringUtils.isBlank(actionConfiguration.getName())) {
            errors.add(actionConfigError(actionConfiguration, "The action configuration name cannot be null or empty"));
        }

        if (StringUtils.isBlank(actionConfiguration.getType())) {
            errors.add(actionConfigError(actionConfiguration, "The action configuration type cannot be null or empty"));
            return errors;
        }

        errors.addAll(validateAgainstSchema(actionConfiguration));
        return errors;
    }

    private List<FlowConfigError> validateAgainstSchema(ActionConfiguration actionConfiguration) {
        return pluginService.getByActionClass(actionConfiguration.getType())
                .map(actionDescriptor -> {
                    List<FlowConfigError> errors = new ArrayList<>();
                    // Check if the plugin providing this action is ready
                    String notReadyReason = pluginService.getActionPluginNotReadyReason(actionConfiguration.getType());
                    if (notReadyReason != null) {
                        errors.add(pluginNotReadyError(actionConfiguration, notReadyReason));
                    }
                    errors.addAll(validateAgainstSchema(actionDescriptor, actionConfiguration));
                    return errors;
                })
                .orElseGet(() -> Collections.singletonList(notRegisteredError(actionConfiguration)));
    }

    FlowConfigError pluginNotReadyError(ActionConfiguration actionConfiguration, String reason) {
        FlowConfigError error = new FlowConfigError();
        error.setConfigName(actionConfiguration.getName());
        error.setErrorType(FlowErrorType.INVALID_CONFIG);
        error.setMessage("Action " + actionConfiguration.getType() + " unavailable: " + reason);
        return error;
    }

    public List<FlowConfigError> validateAgainstSchema(ActionDescriptor actionDescriptor, ActionConfiguration actionConfiguration) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (!actionDescriptor.isSupportsJoin() && actionConfiguration.getJoin() != null) {
            errors.add(FlowConfigError.newBuilder()
                    .configName(actionConfiguration.getName())
                    .errorType(FlowErrorType.INVALID_CONFIG)
                    .message("Attempted to configure join in an action that does not support join")
                    .build());
        }

        SchemaComplianceUtil.validateParameters(actionConfiguration, actionDescriptor).ifPresent(errors::add);

        actionConfiguration.validate(actionDescriptor).stream()
                .map(message -> actionConfigError(actionConfiguration, message))
                .forEach(errors::add);

        return errors;
    }


    FlowConfigError notRegisteredError(ActionConfiguration actionConfiguration) {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName(actionConfiguration.getName());
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: " + actionConfiguration.getType() + " has not been registered with the system");
        return actionConfigError;
    }

    FlowConfigError actionConfigError(ActionConfiguration actionConfiguration, String message) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(actionConfiguration.getName());
        configError.setErrorType(FlowErrorType.INVALID_CONFIG);
        configError.setMessage(message);
        return configError;
    }
}
