/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.generator.flows;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.DomainActionConfiguration;
import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.common.types.FormatActionConfiguration;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.common.types.ValidateActionConfiguration;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;

import java.util.List;
import java.util.Map;

public class ActionUtil {

    public static final List<String> DEFAULT_DOMAINS = List.of("binary");
    public static final String EGRESS_VAR_NAME = "egressUrl";
    public static final String SAMPLE_STRING = "sampleString";
    public static final String SAMPLE_NUMBER = "sampleNumber";
    public static final String SAMPLE_BOOLEAN = "sampleBoolean";
    public static final String SAMPLE_LIST = "sampleList";
    public static final String SAMPLE_MAP = "sampleMap";

    static final ActionGeneratorInput DEFAULT_LOAD = new ActionGeneratorInput("LoadAction", "org.deltafi.passthrough.action.RoteLoadAction");
    static final ActionGeneratorInput DEFAULT_FORMAT = new ActionGeneratorInput("FormatAction", "org.deltafi.passthrough.action.RoteFormatAction");
    static final ActionGeneratorInput DEFAULT_EGRESS = new ActionGeneratorInput("EgressAction", "org.deltafi.core.action.RestPostEgressAction");
    static final Map<String, Object> EGRESS_PARAMS = Map.of("url", "${" + EGRESS_VAR_NAME + "}", "metadataKey", "deltafiMetadata");

    static final String VAR_TEMPLATE = "${%s}";
    static final Map<String, Object> SAMPLE_PARAMS = Map.of(
            SAMPLE_STRING, VAR_TEMPLATE.formatted(SAMPLE_STRING),
            SAMPLE_NUMBER, VAR_TEMPLATE.formatted(SAMPLE_NUMBER),
            SAMPLE_BOOLEAN, VAR_TEMPLATE.formatted(SAMPLE_BOOLEAN),
            SAMPLE_LIST, VAR_TEMPLATE.formatted(SAMPLE_LIST),
            SAMPLE_MAP, VAR_TEMPLATE.formatted(SAMPLE_MAP)
    );

    private ActionUtil() {}

    public static List<TransformActionConfiguration> transformActionConfigurations(List<ActionGeneratorInput> actions) {
        return notEmpty(actions) ? actions.stream().map(ActionUtil::transformActionConfiguration).toList() : null;
    }

    public static TransformActionConfiguration transformActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        TransformActionConfiguration actionConfig = new TransformActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName());
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<LoadActionConfiguration> loadActionConfigurations(List<ActionGeneratorInput> actions) {
        return useDefaultIfEmpty(actions, DEFAULT_LOAD).stream().map(ActionUtil::loadActionConfiguration).toList();
    }

    public static LoadActionConfiguration loadActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        LoadActionConfiguration actionConfig = new LoadActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName());
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<DomainActionConfiguration> domainActionConfigurations(List<ActionGeneratorInput> actions) {
        return notEmpty(actions) ? actions.stream().map(ActionUtil::domainActionConfiguration).toList() : null;
    }

    public static DomainActionConfiguration domainActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        DomainActionConfiguration actionConfig = new DomainActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName(), DEFAULT_DOMAINS);
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<EnrichActionConfiguration> enrichActionConfigurations(List<ActionGeneratorInput> actions) {
        return notEmpty(actions) ? actions.stream().map(ActionUtil::enrichActionConfiguration).toList() : null;
    }

    public static EnrichActionConfiguration enrichActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        EnrichActionConfiguration actionConfig = new EnrichActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName(), DEFAULT_DOMAINS);
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<FormatActionConfiguration> formatActionConfigurations(List<ActionGeneratorInput> actions) {
        return useDefaultIfEmpty(actions, DEFAULT_FORMAT).stream().map(ActionUtil::formatActionConfiguration).toList();
    }

    public static FormatActionConfiguration formatActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        FormatActionConfiguration actionConfig = new FormatActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName(), DEFAULT_DOMAINS);
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<ValidateActionConfiguration> validateActionConfigurations(List<ActionGeneratorInput> actions) {
        return notEmpty(actions) ? actions.stream().map(ActionUtil::validateActionConfiguration).toList() : null;
    }

    public static ValidateActionConfiguration validateActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        ValidateActionConfiguration actionConfig = new ValidateActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName());
        return addParams(actionConfig, actionGeneratorInput);
    }

    public static List<EgressActionConfiguration> egressActionConfigurations(List<ActionGeneratorInput> actions) {
        return useDefaultIfEmpty(actions, DEFAULT_EGRESS).stream().map(ActionUtil::egressActionConfiguration).toList();
    }

    public static EgressActionConfiguration egressActionConfiguration(ActionGeneratorInput actionGeneratorInput) {
        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration(actionGeneratorInput.getClassName(), actionGeneratorInput.getFullClassName());
        if (actionGeneratorInput.equals(DEFAULT_EGRESS)) {
            egressActionConfiguration.setParameters(EGRESS_PARAMS);
        } else if (StringUtils.isNotBlank(actionGeneratorInput.getParameterClassName())) {
            egressActionConfiguration.setParameters(SAMPLE_PARAMS);
        }
        return egressActionConfiguration;
    }

    private static <T extends ActionConfiguration> T addParams(T actionConfig, ActionGeneratorInput actionGeneratorInput) {
        if (StringUtils.isNotBlank(actionGeneratorInput.getParameterClassName())) {
            actionConfig.setParameters(SAMPLE_PARAMS);
        }
        return actionConfig;
    }

    private static List<ActionGeneratorInput> useDefaultIfEmpty(List<ActionGeneratorInput> actions, ActionGeneratorInput defaultAction) {
        if (actions == null || actions.isEmpty()) {
            return List.of(defaultAction);
        }

        return actions;
    }

    public static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static boolean notEmpty(List<?> list) {
        return !(isEmpty(list));
    }
    
}
