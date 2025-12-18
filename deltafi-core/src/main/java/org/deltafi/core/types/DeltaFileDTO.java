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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.LogMessage;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data Transfer Object for exporting/importing DeltaFile entities.
 * This DTO excludes JPA-specific fields (version) and transforms
 * entity relationships into portable representations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeltaFileDTO {

    private UUID did;
    private String name;
    private String dataSource;

    @Builder.Default
    private List<UUID> parentDids = new ArrayList<>();

    private UUID joinId;

    @Builder.Default
    private List<UUID> childDids = new ArrayList<>();

    @Builder.Default
    private Set<DeltaFileFlowDTO> flows = new LinkedHashSet<>();

    private int requeueCount;
    private long ingressBytes;
    private long referencedBytes;
    private long totalBytes;

    private DeltaFileStage stage;

    @Builder.Default
    private Map<String, String> annotations = new HashMap<>();

    private OffsetDateTime created;
    private OffsetDateTime modified;
    private Boolean egressed;
    private Boolean filtered;
    private OffsetDateTime replayed;
    private UUID replayDid;

    @Builder.Default
    private boolean terminal = false;

    @Builder.Default
    private boolean warnings = false;

    @Builder.Default
    private boolean userNotes = false;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private List<LogMessage> messages = new ArrayList<>();

    @Builder.Default
    private boolean contentDeletable = false;

    private OffsetDateTime contentDeleted;
    private String contentDeletedReason;

    @Builder.Default
    private List<UUID> contentObjectIds = new ArrayList<>();

    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @Builder.Default
    private List<String> transforms = new ArrayList<>();

    @Builder.Default
    private List<String> dataSinks = new ArrayList<>();

    @Builder.Default
    private Boolean paused = false;

    @Builder.Default
    private Boolean waitingForChildren = false;

    public List<Segment> allSegments() {
        return flows != null ? flows.stream()
                .flatMap(f -> f.allSegments().stream())
                .toList() : List.of();
    }

    /**
     * Creates a DTO from a DeltaFile entity.
     *
     * @param entity the DeltaFile entity to convert
     * @return a new DeltaFileExportDto populated from the entity
     */
    public static DeltaFileDTO from(DeltaFile entity) {
        if (entity == null) {
            return null;
        }

        return DeltaFileDTO.builder()
                .did(entity.getDid())
                .name(entity.getName())
                .dataSource(entity.getDataSource())
                .parentDids(copyList(entity.getParentDids()))
                .joinId(entity.getJoinId())
                .childDids(copyList(entity.getChildDids()))
                .flows(entity.getFlows() != null
                        ? entity.getFlows().stream()
                        .map(DeltaFileFlowDTO::from)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        : new LinkedHashSet<>())
                .requeueCount(entity.getRequeueCount())
                .ingressBytes(entity.getIngressBytes())
                .referencedBytes(entity.getReferencedBytes())
                .totalBytes(entity.getTotalBytes())
                .stage(entity.getStage())
                .annotations(entity.annotationMap())
                .created(entity.getCreated())
                .modified(entity.getModified())
                .egressed(entity.getEgressed())
                .filtered(entity.getFiltered())
                .replayed(entity.getReplayed())
                .replayDid(entity.getReplayDid())
                .terminal(entity.isTerminal())
                .warnings(entity.isWarnings())
                .userNotes(entity.isUserNotes())
                .pinned(entity.isPinned())
                .messages(copyList(entity.getMessages()))
                .contentDeletable(entity.isContentDeletable())
                .contentDeleted(entity.getContentDeleted())
                .contentDeletedReason(entity.getContentDeletedReason())
                .contentObjectIds(copyList(entity.getContentObjectIds()))
                .topics(copyList(entity.getTopics()))
                .transforms(copyList(entity.getTransforms()))
                .dataSinks(copyList(entity.getDataSinks()))
                .paused(entity.getPaused())
                .waitingForChildren(entity.getWaitingForChildren())
                .build();
    }

    /**
     * Helper to create a defensive copy of a list.
     */
    private static <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}