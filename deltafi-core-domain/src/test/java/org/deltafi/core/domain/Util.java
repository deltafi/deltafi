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
package org.deltafi.core.domain;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.DeltaFileStage;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

public class Util {
    public static DeltaFile buildDeltaFile(String did) {
        return emptyDeltaFile(did, null);
    }

    public static DeltaFile emptyDeltaFile(String did, String flow) {
        OffsetDateTime now = OffsetDateTime.now();
        return buildDeltaFile(did, flow, DeltaFileStage.INGRESS, now, now);
    }

    public static DeltaFile buildDeltaFile(String did, String flow, DeltaFileStage stage, OffsetDateTime created,
                                           OffsetDateTime modified) {
        Action ingressAction = Action.newBuilder()
                .name(INGRESS_ACTION)
                .state(ActionState.COMPLETE)
                .created(created)
                .modified(modified)
                .build();

        return DeltaFile.newBuilder()
                .did(did)
                .sourceInfo(new SourceInfo(null, flow, new ArrayList<>()))
                .stage(stage)
                .created(created)
                .modified(modified)
                .actions(Stream.of(ingressAction).collect(Collectors.toCollection(ArrayList::new)))
                .protocolStack(new ArrayList<>())
                .domains(new ArrayList<>())
                .enrichment(new ArrayList<>())
                .formattedData(new ArrayList<>())
                .egressed(false)
                .filtered(false)
                .build();
    }

    public static void assertEqualsIgnoringDates(DeltaFile expected, DeltaFile actual) {
        Assertions.assertThat(actual.getDid()).isEqualTo(expected.getDid());
        Assertions.assertThat(actual.getStage()).isEqualTo(expected.getStage());
        assertActionsEqualIgnoringDates(expected.getActions(), actual.getActions());
        Assertions.assertThat(actual.getSourceInfo()).isEqualTo(expected.getSourceInfo());
        Assertions.assertThat(actual.getProtocolStack()).isEqualTo(expected.getProtocolStack());
        Assertions.assertThat(actual.getDomains()).isEqualTo(expected.getDomains());
        Assertions.assertThat(actual.getEnrichment()).isEqualTo(expected.getEnrichment());
        Assertions.assertThat(actual.getFormattedData()).isEqualTo(expected.getFormattedData());
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
        }
    }

    public static boolean equalIgnoringDates(DeltaFile d1, DeltaFile d2) {
        return java.util.Objects.equals(d1.getDid(), d2.getDid()) &&
                java.util.Objects.equals(d1.getStage(), d2.getStage()) &&
                actionsEqualIgnoringDates(d1.getActions(), d2.getActions()) &&
                java.util.Objects.equals(d1.getSourceInfo(), d2.getSourceInfo()) &&
                java.util.Objects.equals(d1.getProtocolStack(), d2.getProtocolStack()) &&
                java.util.Objects.equals(d1.getDomains(), d2.getDomains()) &&
                java.util.Objects.equals(d1.getEnrichment(), d2.getEnrichment()) &&
                java.util.Objects.equals(d1.getFormattedData(), d2.getFormattedData()) &&
                java.util.Objects.equals(d1.getEgressed(), d2.getEgressed()) &&
                java.util.Objects.equals(d1.getFiltered(), d2.getFiltered());
    }

    public static boolean actionsEqualIgnoringDates(List<Action> a1, List<Action> a2) {
        if (a1 == null && a2 == null) {
            return true;
        } else if (a1 == null || a2 == null) {
            return false;
        } else {
            for (int i = 0; i < a1.size(); i++) {
                if (!actionEqualIgnoringDates(a1.get(i), a2.get(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean actionEqualIgnoringDates(Action a1, Action a2) {
        if (a1 == null && a2 == null) {
            return true;
        } else if (a1 == null || a2 == null) {
            return false;
        } else {
            return java.util.Objects.equals(a1.getName(), a2.getName()) &&
                    java.util.Objects.equals(a1.getState(), a2.getState()) &&
                    java.util.Objects.equals(a1.getErrorContext(), a2.getErrorContext()) &&
                    java.util.Objects.equals(a1.getErrorCause(), a2.getErrorCause());
        }
    }
}