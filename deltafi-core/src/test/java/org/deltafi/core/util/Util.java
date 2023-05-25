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
package org.deltafi.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.core.generated.types.ErrorsByMessage;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Util {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String FLOW = "myFlow";

    public static DeltaFile buildDeltaFile(String did) {
        return buildDeltaFile(did, List.of());
    }

    public static DeltaFile buildDeltaFile(String did, List<Content> content) {
        return emptyDeltaFile(did, FLOW, content);
    }

    public static DeltaFile buildDeltaFile(String did, List<Content> content, Map<String, String> metadata) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, null, DeltaFileStage.INGRESS, now, now, content, metadata);
    }

    public static DeltaFile emptyDeltaFile(String did, String flow) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, flow, DeltaFileStage.INGRESS, now, now, new ArrayList<>());
    }

    public static DeltaFile emptyDeltaFile(String did, String flow, List<Content> content) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, flow, DeltaFileStage.INGRESS, now, now, content);
    }

    public static DeltaFile emptyDeltaFile(String did, String flow, List<Content> content, Map<String, String> metadata) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, flow, DeltaFileStage.INGRESS, now, now, content, metadata);
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified) {
        return buildDeltaFile(did, flow, stage, created, modified, new ArrayList<>(), new HashMap<>());
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified, List<Content> content) {
        return buildDeltaFile(did, flow, stage, created, modified, content, new HashMap<>());
    }

    public static DeltaFile buildErrorDeltaFile(
            String did, String flow, String cause, String context, OffsetDateTime created) {
        return buildErrorDeltaFile(did, flow, cause, context, created, created, true, true, null, List.of());
    }

    public static DeltaFile buildErrorDeltaFile(String did, String flow, String cause, String context,
                                                OffsetDateTime created, OffsetDateTime modified, boolean extraAction,
                                                boolean errorIsLast, String extraError) {
        return buildErrorDeltaFile(did, flow, cause, context, created, modified, extraAction, errorIsLast, extraError, new ArrayList<>());
    }

    public static DeltaFile buildErrorDeltaFile(String did, String flow, String cause, String context, OffsetDateTime created,
                                                OffsetDateTime modified, boolean extraAction, boolean errorIsLast,
                                                String extraError, List<Content> content) {

        DeltaFile deltaFile = Util.buildDeltaFile(did, flow, DeltaFileStage.ERROR, created, modified, content);
        if (extraAction) {
            if (!errorIsLast) {
                deltaFile.queueNewAction("ErrorAction", ActionType.UNKNOWN);
                deltaFile.errorAction("ErrorAction", modified, modified, cause, context);
            }
            deltaFile.queueNewAction("OtherAction", ActionType.UNKNOWN);
            deltaFile.completeAction(ActionEvent.newBuilder()
                    .action("OtherAction")
                    .start(modified)
                    .stop(modified)
                    .build());
        }

        if (errorIsLast || !extraAction) {
            deltaFile.queueNewAction("ErrorAction", ActionType.UNKNOWN);
            deltaFile.errorAction("ErrorAction", modified, modified, cause, context);
        }

        if (extraError != null) {
            deltaFile.queueNewAction("AnotherErrorAction", ActionType.UNKNOWN);
            deltaFile.errorAction("AnotherErrorAction", modified, modified, extraError, context);
        }
        deltaFile.setModified(modified);

        return deltaFile;
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified, List<Content> content, Map<String, String> metadata) {
        Action ingressAction = Action.newBuilder()
                .name(INGRESS_ACTION)
                .type(ActionType.INGRESS)
                .state(ActionState.COMPLETE)
                .created(created)
                .modified(modified)
                .content(content)
                .metadata(metadata)
                .build();

        return DeltaFile.newBuilder()
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(did)
                .parentDids(new ArrayList<>())
                .childDids(new ArrayList<>())
                .ingressBytes(0L)
                .sourceInfo(new SourceInfo("filename", flow, metadata))
                .stage(stage)
                .created(created)
                .modified(modified)
                .actions(Stream.of(ingressAction).collect(Collectors.toCollection(ArrayList::new)))
                .domains(new ArrayList<>())
                .enrichment(new ArrayList<>())
                .egress(new ArrayList<>())
                .formattedData(new ArrayList<>())
                .egressed(false)
                .filtered(false)
                .build();
    }

    public static void assertEqualsIgnoringDates(DeltaFile expected, DeltaFile actual) {
        Assertions.assertThat(actual.getDid()).isEqualTo(expected.getDid());
        Assertions.assertThat(actual.getStage()).isEqualTo(expected.getStage());
        Assertions.assertThat(actual.getChildDids()).isEqualTo(expected.getChildDids());
        Assertions.assertThat(actual.getParentDids()).isEqualTo(expected.getParentDids());
        Assertions.assertThat(actual.getIngressBytes()).isEqualTo(expected.getIngressBytes());
        assertActionsEqualIgnoringDates(expected.getActions(), actual.getActions());
        Assertions.assertThat(actual.getSourceInfo()).isEqualTo(expected.getSourceInfo());
        Assertions.assertThat(actual.getDomains()).isEqualTo(expected.getDomains());
        Assertions.assertThat(actual.getAnnotations()).isEqualTo(expected.getAnnotations());
        Assertions.assertThat(actual.getAnnotationKeys()).isEqualTo(expected.getAnnotationKeys());
        Assertions.assertThat(actual.getEnrichment()).isEqualTo(expected.getEnrichment());
        Assertions.assertThat(actual.getFormattedData()).isEqualTo(expected.getFormattedData());
        Assertions.assertThat(actual.getEgressed()).isEqualTo(expected.getEgressed());
        Assertions.assertThat(actual.getFiltered()).isEqualTo(expected.getFiltered());
        Assertions.assertThat(actual.getEgress()).isEqualTo(expected.getEgress());
        Assertions.assertThat(actual.getTestMode()).isEqualTo(expected.getTestMode());
        Assertions.assertThat(actual.getContentDeleted() == null).isEqualTo(expected.getContentDeleted() == null);
        Assertions.assertThat(actual.getContentDeletedReason()).isEqualTo(expected.getContentDeletedReason());
        Assertions.assertThat(actual.getErrorAcknowledged() == null).isEqualTo(expected.getErrorAcknowledged() == null);
        Assertions.assertThat(actual.getErrorAcknowledgedReason()).isEqualTo(expected.getErrorAcknowledgedReason());
        Assertions.assertThat(actual.getNextAutoResume() == null).isEqualTo(expected.getNextAutoResume() == null);
        Assertions.assertThat(actual.getNextAutoResumeReason()).isEqualTo(expected.getNextAutoResumeReason());
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
            Assertions.assertThat(actual.getState()).isEqualTo(expected.getState());
            Assertions.assertThat(actual.getErrorCause()).isEqualTo(expected.getErrorCause());
            if (expected.getErrorContext() != null
                    && expected.getErrorContext().startsWith("STARTS:")) {
                Assertions.assertThat(actual.getErrorContext()).startsWith(
                        expected.getErrorContext().substring(7));
            } else {
                Assertions.assertThat(actual.getErrorContext()).isEqualTo(expected.getErrorContext());
            }
            Assertions.assertThat(actual.getAttempt()).isEqualTo(expected.getAttempt());
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
            return OBJECT_MAPPER.readValue(Util.class.getClassLoader().getResource(resourcePath), clazz);
        } catch (IOException e) {
            org.junit.jupiter.api.Assertions.fail(e);
        }
        return null;
    }

    public static ActionEvent actionEvent(String filename, String did) throws IOException {
        String json = String.format(new String(Objects.requireNonNull(Util.class.getClassLoader().getResourceAsStream("full-flow/" + filename + ".json")).readAllBytes()), did);
        return OBJECT_MAPPER.readValue(json, ActionEvent.class);
    }

    public static ActionEvent filterActionEvent(String did, String filteredAction) throws IOException {
        String json = String.format(new String(Objects.requireNonNull(Util.class.getClassLoader().getResourceAsStream("full-flow/filter.json")).readAllBytes()), did, filteredAction);
        return OBJECT_MAPPER.readValue(json, ActionEvent.class);
    }

    public static String graphQL(String filename) throws IOException {
        return new String(Objects.requireNonNull(Util.class.getClassLoader().getResourceAsStream("full-flow/" + filename + ".graphql")).readAllBytes());
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
        return Variable.newBuilder()
                .name(name)
                .dataType(VariableDataType.STRING)
                .description("describe " + defaultValue)
                .defaultValue(defaultValue)
                .value(value)
                .required(false)
                .build();
    }

    public static void matchesCounterPerMessage(ErrorsByMessage result, int index, String cause, String flow, List<String> dids) {
        assertEquals(cause, result.getCountPerMessage().get(index).getMessage());
        assertEquals(flow, result.getCountPerMessage().get(index).getFlow());
        assertEquals(dids.size(), result.getCountPerMessage().get(index).getCount());
        assertEquals(dids.size(), result.getCountPerMessage().get(index).getDids().size());
        assertTrue(result.getCountPerMessage().get(index).getDids().containsAll(dids));
    }
}
