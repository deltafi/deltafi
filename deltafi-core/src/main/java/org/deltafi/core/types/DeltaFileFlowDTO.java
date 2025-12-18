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
package org.deltafi.core.types;

import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class DeltaFileFlowDTO {

    private UUID id;

    private String name;
    private FlowType type;
    private int number;
    private DeltaFileFlowState state;
    private OffsetDateTime created;
    private OffsetDateTime modified;
    private DeltaFileFlowInput input = new DeltaFileFlowInput();
    private List<Action> actions = new ArrayList<>();
    private List<String> publishTopics = new ArrayList<>();
    private int depth;
    private List<String> pendingAnnotations = new ArrayList<>();
    private boolean testMode;
    private String testModeReason;
    private UUID joinId;
    private List<String> pendingActions = new ArrayList<>();
    private OffsetDateTime errorAcknowledged;
    private String errorAcknowledgedReason;
    private boolean coldQueued;
    private String coldQueuedAction;
    private String errorOrFilterCause;
    private OffsetDateTime nextAutoResume;

    public static DeltaFileFlowDTO from(DeltaFileFlow entity) {
        if (entity == null) {
            return null;
        }

        List<Action> actions = entity.getActions() == null ? new ArrayList<>() :
                entity.getActions().stream().map(Action::new).collect(Collectors.toCollection(ArrayList::new));

        return DeltaFileFlowDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .number(entity.getNumber())
                .state(entity.getState())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .input(entity.getInput())
                .actions(copyList(actions))
                .publishTopics(copyList(entity.getPublishTopics()))
                .depth(entity.getDepth())
                .pendingAnnotations(copyList(entity.getPendingAnnotations()))
                .testMode(entity.testMode)
                .testModeReason(entity.getTestModeReason())
                .joinId(entity.getJoinId())
                .pendingActions(copyList(entity.getPendingActions()))
                .errorAcknowledged(entity.getErrorAcknowledged())
                .errorAcknowledgedReason(entity.getErrorAcknowledgedReason())
                .coldQueued(entity.isColdQueued())
                .coldQueuedAction(entity.getColdQueuedAction())
                .errorOrFilterCause(entity.getErrorOrFilterCause())
                .nextAutoResume(entity.getNextAutoResume())
                .build();
    }

    public List<Segment> allSegments() {
        return actions.stream()
                .flatMap(a -> a.getContent().stream())
                .flatMap(c -> c.getSegments().stream())
                .toList();
    }

    private static <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
