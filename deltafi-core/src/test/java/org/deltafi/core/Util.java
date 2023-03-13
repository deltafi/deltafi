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
package org.deltafi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

public class Util {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String FLOW = "myFlow";

    public static DeltaFile buildDeltaFile(String did) {
        return emptyDeltaFile(did, FLOW);
    }

    public static DeltaFile buildDeltaFile(String did, Map<String, String> metadata) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, null, DeltaFileStage.INGRESS, now, now, metadata);
    }

    public static DeltaFile emptyDeltaFile(String did, String flow) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, flow, DeltaFileStage.INGRESS, now, now);
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified) {
        return buildDeltaFile(did, flow, stage, created, modified, new HashMap<>());

    }

    public static DeltaFile buildErrorDeltaFile(
            String did, String flow, String cause, String context,
            OffsetDateTime created) {
        return buildErrorDeltaFile(did, flow, cause, context, created, created, true, true, null);
    }

    public static DeltaFile buildErrorDeltaFile(
            String did, String flow, String cause, String context,
            OffsetDateTime created, OffsetDateTime modified,
            boolean extraAction, boolean errorIsLast, String extraError) {

        DeltaFile deltaFile = Util.buildDeltaFile(did, flow, DeltaFileStage.ERROR, created, modified);
        if (extraAction) {
            if (!errorIsLast) {
                deltaFile.queueNewAction("ErrorAction");
                deltaFile.errorAction("ErrorAction", modified, modified, cause, context);
            }
            deltaFile.queueNewAction("OtherAction");
            deltaFile.completeAction(ActionEventInput.newBuilder()
                    .action("OtherAction")
                    .start(modified)
                    .stop(modified)
                    .build());
        }

        if (errorIsLast || !extraAction) {
            deltaFile.queueNewAction("ErrorAction");
            deltaFile.errorAction("ErrorAction", modified, modified, cause, context);
        }

        if (extraError != null) {
            deltaFile.queueNewAction("AnotherErrorAction");
            deltaFile.errorAction("AnotherErrorAction", modified, modified, extraError, context);
        }
        deltaFile.setModified(modified);

        return deltaFile;
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified, Map<String, String> metadata) {
        Action ingressAction = Action.newBuilder()
                .name(INGRESS_ACTION)
                .state(ActionState.COMPLETE)
                .created(created)
                .modified(modified)
                .build();

        return DeltaFile.newBuilder()
                .did(did)
                .parentDids(new ArrayList<>())
                .childDids(new ArrayList<>())
                .ingressBytes(0L)
                .sourceInfo(new SourceInfo(null, flow, metadata))
                .stage(stage)
                .created(created)
                .modified(modified)
                .actions(Stream.of(ingressAction).collect(Collectors.toCollection(ArrayList::new)))
                .protocolStack(new ArrayList<>())
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
        Assertions.assertThat(actual.getProtocolStack()).isEqualTo(expected.getProtocolStack());
        Assertions.assertThat(actual.getDomains()).isEqualTo(expected.getDomains());
        Assertions.assertThat(actual.getIndexedMetadata()).isEqualTo(expected.getIndexedMetadata());
        Assertions.assertThat(actual.getIndexedMetadataKeys()).isEqualTo(expected.getIndexedMetadataKeys());
        Assertions.assertThat(actual.getEnrichment()).isEqualTo(expected.getEnrichment());
        Assertions.assertThat(actual.getFormattedData()).isEqualTo(expected.getFormattedData());
        Assertions.assertThat(actual.getEgressed()).isEqualTo(expected.getEgressed());
        Assertions.assertThat(actual.getFiltered()).isEqualTo(expected.getFiltered());
        Assertions.assertThat(actual.getEgress()).isEqualTo(expected.getEgress());
        Assertions.assertThat(actual.getTestMode()).isEqualTo(expected.getTestMode());
        Assertions.assertThat(actual.getNextAutoResume() == null).isEqualTo(expected.getNextAutoResume() == null);
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
            Assertions.assertThat(actual.getErrorContext()).isEqualTo(expected.getErrorContext());
            Assertions.assertThat(actual.getErrorCause()).isEqualTo(expected.getErrorCause());
            Assertions.assertThat(actual.getAttempt()).isEqualTo(expected.getAttempt());
        }
    }

    public static PropertySet getPropertySet(String name) {
        PropertySet propertySet = new PropertySet();
        propertySet.setId(name);
        propertySet.setDisplayName(name);
        propertySet.setDescription("some property set");
        return propertySet;
    }

    public static PropertySet getPropertySetWithProperty(String name) {
        PropertySet propertySet = getPropertySet(name);
        propertySet.getProperties().add(getProperty());
        return propertySet;
    }

    public static Property getProperty() {
        return getProperty("a", "a-value", true);
    }

    public static Property getProperty(String name, String value, boolean editable) {
        return Property.builder()
                .key(name)
                .editable(editable)
                .defaultValue("default it")
                .description("some property")
                .value(value).build();
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
}
