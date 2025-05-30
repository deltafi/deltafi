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
package org.deltafi.core.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.core.services.CoreEventQueue;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.core.util.FlowBuilders.TRANSFORM_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Service
@RequiredArgsConstructor
public class UtilService {
    private final FlowDefinitionService flowDefinitionService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String FLOW = "myFlow";
    private static final Clock clock = Clock.tickMillis(ZoneOffset.UTC);
    private static final SchemaGenerator SCHEMA_GENERATOR = schemaGenerator();

    public DeltaFile buildDeltaFile(UUID did) {
        return buildDeltaFile(did, List.of());
    }

    public DeltaFile buildDeltaFile(UUID did, List<Content> content) {
        return emptyDeltaFile(did, FLOW, content);
    }

    public DeltaFile buildDeltaFile(UUID did, List<Content> content, Map<String, String> metadata) {
        OffsetDateTime now = now();
        return buildDeltaFile(did, "dataSource", DeltaFileStage.IN_FLIGHT, now, now, content, metadata);
    }

    public DeltaFile emptyDeltaFile(UUID did, String dataSource) {
        OffsetDateTime now = now();
        return buildDeltaFile(did, dataSource, DeltaFileStage.IN_FLIGHT, now, now, new ArrayList<>());
    }

    public DeltaFile emptyDeltaFile(UUID did, String dataSource, List<Content> content) {
        OffsetDateTime now = now();
        return buildDeltaFile(did, dataSource, DeltaFileStage.IN_FLIGHT, now, now, content);
    }

    public DeltaFile buildDeltaFile(UUID did, String dataSource, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified) {
        return buildDeltaFile(did, dataSource, stage, created, modified, new ArrayList<>(), new HashMap<>());
    }

    public DeltaFile buildDeltaFile(UUID did, String dataSource, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified, List<Content> content) {
        return buildDeltaFile(did, dataSource, stage, created, modified, content, new HashMap<>());
    }

    public DeltaFile buildErrorDeltaFile(
            UUID did, String dataSource, String cause, String context, OffsetDateTime created) {
        return buildErrorDeltaFile(did, dataSource, cause, context, created, created, null, List.of());
    }

    public DeltaFile buildErrorDeltaFile(UUID did, String dataSource, String cause, String context,
                                                OffsetDateTime created, OffsetDateTime modified, String extraError) {
        return buildErrorDeltaFile(did, dataSource, cause, context, created, modified, extraError, new ArrayList<>());
    }

    public DeltaFile buildErrorDeltaFile(UUID did, String dataSource, String cause, String context, OffsetDateTime created,
                                                OffsetDateTime modified, String extraError, List<Content> content) {

        DeltaFile deltaFile = buildDeltaFile(did, "ingressFlow", DeltaFileStage.COMPLETE, created, modified, content);
        deltaFile.firstFlow().getActions().getFirst().setState(ActionState.COMPLETE);
        DeltaFileFlow firstFlow = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(dataSource, FlowType.TRANSFORM), deltaFile.firstFlow(), created);
        Action errorAction = firstFlow.queueNewAction("ErrorAction", null, ActionType.TRANSFORM, false, created);
        errorAction.error(modified, modified, modified, cause, context);
        firstFlow.updateState();

        if (extraError != null) {
            DeltaFileFlow secondFlow = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow("extraFlow", FlowType.TRANSFORM), deltaFile.firstFlow(), created);
            Action anotherErrorAction = secondFlow.queueNewAction("AnotherErrorAction", null, ActionType.TRANSFORM, false, created);
            anotherErrorAction.error(modified, modified, modified, extraError, context);
            secondFlow.updateState();
        }
        deltaFile.updateState(modified);

        return deltaFile;
    }

    public DeltaFile buildDeltaFile(UUID did, String dataSource, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified, List<Content> content, Map<String, String> metadata) {
        Action ingressAction = Action.builder()
                .name(INGRESS_ACTION)
                .type(ActionType.INGRESS)
                .state(stage == DeltaFileStage.ERROR ? ActionState.ERROR : ActionState.COMPLETE)
                .created(created)
                .modified(modified)
                .content(content)
                .metadata(metadata)
                .build();

        DeltaFileFlow flow = DeltaFileFlow.builder()
                .flowDefinition(flowDefinitionService.getOrCreateFlow(dataSource, FlowType.TIMED_DATA_SOURCE))
                .number(0)
                .state(DeltaFileFlowState.COMPLETE)
                .created(created)
                .modified(modified)
                .input(new DeltaFileFlowInput())
                .publishTopics(List.of(TRANSFORM_TOPIC))
                .actions(new ArrayList<>(List.of(ingressAction)))
                .build();

        DeltaFile deltaFile = buildDeltaFile(did, dataSource);
        deltaFile.setStage(stage);
        deltaFile.setCreated(created);
        deltaFile.setModified(modified);
        deltaFile.getFlows().add(flow);
        deltaFile.setVersion(1);

        flow.updateState();
        deltaFile.updateFlags();
        deltaFile.wireBackPointers();
        return deltaFile;
    }

    public static DeltaFile buildDeltaFile(UUID did, String dataSource) {
        DeltaFile deltaFile = DeltaFile.builder()
                .did(did)
                .parentDids(new ArrayList<>())
                .childDids(new ArrayList<>())
                .ingressBytes(0L)
                .name("filename")
                .dataSource(dataSource)
                .flows(new LinkedHashSet<>())
                .egressed(false)
                .filtered(false)
                .totalBytes(1)
                .build();

        deltaFile.updateFlags();
        return deltaFile;
    }

    public static void assertEqualsIgnoringDates(DeltaFile expected, DeltaFile actual) {
        Assertions.assertThat(actual.getDid()).isEqualTo(expected.getDid());
        Assertions.assertThat(actual.getStage()).isEqualTo(expected.getStage());
        Assertions.assertThat(actual.getChildDids()).isEqualTo(expected.getChildDids());
        Assertions.assertThat(actual.getParentDids()).isEqualTo(expected.getParentDids());
        Assertions.assertThat(actual.getIngressBytes()).isEqualTo(expected.getIngressBytes());
        assertFlowsEqualIgnoringDates(expected.getFlows(), actual.getFlows());
        Assertions.assertThat(actual.getDataSource()).isEqualTo(expected.getDataSource());
        Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
        Assertions.assertThat(actual.annotationMap()).isEqualTo(expected.annotationMap());
        Assertions.assertThat(actual.getEgressed()).isEqualTo(expected.getEgressed());
        Assertions.assertThat(actual.getFiltered()).isEqualTo(expected.getFiltered());
        Assertions.assertThat(actual.getContentDeleted() == null).isEqualTo(expected.getContentDeleted() == null);
        Assertions.assertThat(actual.getContentDeletedReason()).isEqualTo(expected.getContentDeletedReason());
    }

    public static void assertFlowsEqualIgnoringDates(Set<DeltaFileFlow> expected, Set<DeltaFileFlow> actual) {
        if (expected == null || actual == null) {
            Assertions.assertThat(actual).isEqualTo(expected);
            return;
        }

        Assertions.assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertFlowEqualIgnoringDates(expected.stream().sorted(Comparator.comparingInt(DeltaFileFlow::getNumber)).toList().get(i), actual.stream().sorted(Comparator.comparingInt(DeltaFileFlow::getNumber)).toList().get(i));
        }
    }

    public static void assertFlowEqualIgnoringDates(DeltaFileFlow expected, DeltaFileFlow actual) {
        if (expected == null || actual == null) {
            Assertions.assertThat(actual).isEqualTo(expected);
        } else {
            Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
            Assertions.assertThat(actual.getNumber()).isEqualTo(expected.getNumber());
            Assertions.assertThat(actual.getType()).isEqualTo(expected.getType());
            Assertions.assertThat(actual.getState()).isEqualTo(expected.getState());
            Assertions.assertThat(actual.getInput().getTopics()).isEqualTo(expected.getInput().getTopics());
            Assertions.assertThat(actual.getInput().getMetadata()).isEqualTo(expected.getInput().getMetadata());
            Assertions.assertThat(actual.getInput().getContent()).isEqualTo(expected.getInput().getContent());
            Assertions.assertThat(actual.getInput().getAncestorIds()).isEqualTo(expected.getInput().getAncestorIds());
            assertActionsEqualIgnoringDates(expected.getActions(), actual.getActions());
            Assertions.assertThat(actual.getPublishTopics()).isEqualTo(expected.getPublishTopics());
            Assertions.assertThat(actual.getDepth()).isEqualTo(expected.getDepth());
            Assertions.assertThat(actual.hasPendingAnnotations()).isEqualTo(expected.hasPendingAnnotations());
            Assertions.assertThat(actual.isTestMode()).isEqualTo(expected.isTestMode());
            Assertions.assertThat(actual.getTestModeReason()).isEqualTo(expected.getTestModeReason());
            Assertions.assertThat(actual.getErrorAcknowledgedReason()).isEqualTo(expected.getErrorAcknowledgedReason());
        }
    }

    public static void assertActionsEqualIgnoringDates(List<Action> expected, List<Action> actual) {
        if (expected == null || actual == null) {
            Assertions.assertThat(actual).isEqualTo(expected);
            return;
        }

        Assertions.assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertActionEqualIgnoringDates(expected.get(i), actual.get(i));
        }
    }

    public static void assertActionEqualIgnoringDates(Action expected, Action actual) {
        if (expected == null || actual == null) {
            Assertions.assertThat(actual).isEqualTo(expected);
        } else {
            Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
            Assertions.assertThat(actual.getType()).isEqualTo(expected.getType());
            Assertions.assertThat(actual.getState()).isEqualTo(expected.getState());
            Assertions.assertThat(actual.getErrorCause()).isEqualTo(expected.getErrorCause());
            if (expected.getErrorContext() != null
                    && expected.getErrorContext().startsWith("STARTS:")) {
                Assertions.assertThat(actual.getErrorContext()).startsWith(
                        expected.getErrorContext().substring(7));
            } else {
                Assertions.assertThat(actual.getErrorContext()).isEqualTo(expected.getErrorContext());
            }
            Assertions.assertThat(actual.getNextAutoResumeReason()).isEqualTo(expected.getNextAutoResumeReason());
            Assertions.assertThat(actual.getFilteredCause()).isEqualTo(expected.getFilteredCause());
            Assertions.assertThat(actual.getFilteredContext()).isEqualTo(expected.getFilteredContext());
            Assertions.assertThat(actual.getAttempt()).isEqualTo(expected.getAttempt());
            Assertions.assertThat(actual.getContent()).isEqualTo(expected.getContent());
            Assertions.assertThat(actual.getMetadata()).isEqualTo(expected.getMetadata());
            Assertions.assertThat(actual.getDeleteMetadataKeys()).isEqualTo(expected.getDeleteMetadataKeys());
        }
    }

    public static ActionDescriptor egressActionDescriptor() {
        return egressActionDescriptor("config-test/rest-egress-action-descriptor.json");
    }

    public static ActionDescriptor egressActionDescriptor(String schemaPath) {
        return readResource(schemaPath, ActionDescriptor.class);
    }

    public static <T> T readResource(String resourcePath, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(UtilService.class.getClassLoader().getResource(resourcePath), clazz);
        } catch (IOException e) {
            org.junit.jupiter.api.Assertions.fail(e);
        }
        return null;
    }

    public static ActionEvent actionEvent(String filename, UUID... dids) throws IOException {
        String json = String.format(new String(Objects.requireNonNull(UtilService.class.getClassLoader().getResourceAsStream("full-flow/" + filename + ".json")).readAllBytes()),
                (Object[]) Arrays.stream(dids).map(UUID::toString).toList().toArray(new String[0]));
        return CoreEventQueue.convertEvent(json);
    }

    public static ActionEvent filterActionEvent(UUID did, String flow, UUID flowId, String filteredAction) throws IOException {
        String json = String.format(new String(Objects.requireNonNull(UtilService.class.getClassLoader().getResourceAsStream("full-flow/filter.json")).readAllBytes()), did, flow, flowId, filteredAction);
        return CoreEventQueue.convertEvent(json);
    }

    public static String graphQL(String filename) throws IOException {
        return new String(Objects.requireNonNull(UtilService.class.getClassLoader().getResourceAsStream("full-flow/" + filename + ".graphql")).readAllBytes());
    }

    public static List<String> getActionNames(List<ActionFamily> actionFamilies, String family) {
        return actionFamilies.stream()
                .filter(actionFamily -> family.equals(actionFamily.getFamily()))
                .map(ActionFamily::getActionNames)
                .flatMap(Collection::stream)
                .toList();
    }

    public static Variable buildOriginalVariable(String name) {
        return buildVariable(name, "set value", "original default value");
    }

    public static Variable buildNewVariable(String name) {
        return buildVariable(name, null, "new default value");
    }

    public static Variable buildVariable(String name, String value, String defaultValue) {
        return Variable.builder()
                .name(name)
                .dataType(VariableDataType.STRING)
                .description("describe " + defaultValue)
                .defaultValue(defaultValue)
                .value(value)
                .required(false)
                .build();
    }

    public static void matchesCounterPerMessage(SummaryByFlowAndMessage result, int index, String cause, String flow, List<UUID> dids) {
        assertEquals(cause, result.countPerMessage().get(index).getMessage());
        assertEquals(flow, result.countPerMessage().get(index).getFlow());
        assertEquals(dids.size(), result.countPerMessage().get(index).getCount());
        assertEquals(dids.size(), result.countPerMessage().get(index).getDids().size());
        assertTrue(result.countPerMessage().get(index).getDids().containsAll(dids));
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public static Action autoResumeIngress(OffsetDateTime time) {
        return Action.builder().name("ingress").modified(time).state(ActionState.COMPLETE).build();
    }

    public static Action autoResumeHit(OffsetDateTime time) {
        Action hit = Action.builder().name("hit").modified(time).state(ActionState.ERROR).build();
        hit.setNextAutoResume(time.minusSeconds(1000));
        return hit;
    }

    public static Action autoResumeMiss(OffsetDateTime time) {
        Action miss = Action.builder().name("miss").modified(time).state(ActionState.ERROR).build();
        miss.setNextAutoResume(time.plusSeconds(1000));
        return miss;
    }

    public static Action autoResumeNotSet(OffsetDateTime time) {
        return Action.builder().name("notSet").modified(time).state(ActionState.ERROR).build();
    }

    public static Action autoResumeOther(OffsetDateTime time) {
        return Action.builder().name("other").modified(time).state(ActionState.COMPLETE).build();
    }

    public static Map<String, Object> generateSchema(Class<?> clazz) {
        return OBJECT_MAPPER.convertValue(SCHEMA_GENERATOR.generateSchema(clazz), new TypeReference<>() {});
    }

    private static SchemaGenerator schemaGenerator() {
        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .without(Option.SCHEMA_VERSION_INDICATOR)
                        .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                        .with(Option.INLINE_ALL_SCHEMAS)
                        .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                                JacksonOption.IGNORE_TYPE_INFO_TRANSFORM,
                                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY));

        configBuilder.forFields()
                .withRequiredCheck(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    return jsonProperty != null && jsonProperty.required();
                })
                .withDefaultResolver(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    if ((jsonProperty == null) || jsonProperty.defaultValue().isEmpty()) {
                        return null;
                    }
                    try {
                        Class<?> type = fieldScope.getDeclaredType().getErasedType();
                        // limit the default types that are mapped, sub-objects should have independent defaults defined as needed
                        if (type.isPrimitive() || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
                            return OBJECT_MAPPER.readValue(jsonProperty.defaultValue(), new TypeReference<>() {});
                        } else {
                            return jsonProperty.defaultValue();
                        }
                    } catch (JsonProcessingException e) {
                        return jsonProperty.defaultValue();
                    }
                });

        configBuilder.forTypesInGeneral()
                .withAdditionalPropertiesResolver(scope ->
                        scope.getType().isInstanceOf(Map.class) ? scope.getTypeParameterFor(Map.class, 1) : null);
        return new SchemaGenerator(configBuilder.build());
    }
}
