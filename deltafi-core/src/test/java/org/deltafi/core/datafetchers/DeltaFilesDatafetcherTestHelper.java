/*
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
package org.deltafi.core.datafetchers;

import org.deltafi.common.content.Segment;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.core.generated.client.DeltaFileProjectionRoot;
import org.deltafi.core.generated.client.DeltaFilesProjectionRoot;
import org.deltafi.core.generated.client.ErrorSummaryByFlowProjectionRoot;
import org.deltafi.core.generated.client.ErrorSummaryByMessageProjectionRoot;
import org.deltafi.core.generated.client.FilteredSummaryByFlowProjectionRoot;
import org.deltafi.core.generated.client.FilteredSummaryByMessageProjectionRoot;

import java.util.*;

import static org.deltafi.core.util.Constants.REST_DATA_SOURCE_NAME;

public class DeltaFilesDatafetcherTestHelper {

    static final String FILENAME = "theFilename";
    static final Long SIZE = 500L;
    static final String OBJECT_UUID = "theUuid";
    static final String OBJECT_UUID_2 = "theUuid2";
    static final String DID = UUID.randomUUID().toString();
    static final String DID_2 = UUID.randomUUID().toString();
    static final Map<String, String> METADATA = Map.of("k1", "v1", "k2", "v2");
    static final String MEDIA_TYPE = "plain/text";
    static final List<Content> CONTENT = Collections.singletonList(new Content(FILENAME, MEDIA_TYPE, new Segment(OBJECT_UUID, 0, SIZE, DID)));
    static final List<Content> CONTENT_2 = Collections.singletonList(new Content(FILENAME, MEDIA_TYPE, new Segment(OBJECT_UUID_2, 0, SIZE, DID)));
    public static final IngressEventItem INGRESS_INPUT = new IngressEventItem(DID, FILENAME, REST_DATA_SOURCE_NAME, METADATA, CONTENT);
    public static final IngressEventItem INGRESS_INPUT_2 = new IngressEventItem(DID_2, FILENAME, REST_DATA_SOURCE_NAME, METADATA, CONTENT_2);

    public static final DeltaFilesProjectionRoot DELTA_FILES_PROJECTION_ROOT = new DeltaFilesProjectionRoot()
            .deltaFiles()
                .did()
                .name()
                .parentDids()
                .childDids()
                .ingressBytes()
                .dataSource()
                .stage()
                .parent()
                .flows()
                .name()
                .id()
                .type()
                .parent()
                .state()
                .parent()
                .created()
                .modified()
                .flowPlan()
                .name()
                .plugin()
                .name()
                .pluginVersion()
                .parent()
                .input()
                .metadata()
                .content()
                .mediaType()
                .name()
                .size()
                .segments()
                .did()
                .size()
                .offset()
                .uuid()
                .parent()
                .parent()
                .topics()
                .ancestorIds()
                .parent()
                .testMode()
                .testModeReason()
                .actions()
                .id()
                .type()
                .parent()
                .content()
                .mediaType()
                .name()
                .size()
                .segments()
                .did()
                .size()
                .offset()
                .uuid()
                .parent()
                .parent()
                .errorAcknowledged()
                .errorAcknowledgedReason()
                .nextAutoResume()
                .nextAutoResumeReason()
                .metadata()
                .name()
                .created()
                .modified()
                .errorCause()
                .errorContext()
                .filteredCause()
                .filteredContext()
                .state()
                .parent()
                .parent()
                .publishTopics()
                .depth()
                .parent()
                .contentDeleted()
                .contentDeletedReason()
                .filtered()
                .egressed()
                .replayed()
            .parent()
            .offset()
            .count()
            .totalCount();

    public static final DeltaFileProjectionRoot DELTA_FILE_PROJECTION_ROOT = new DeltaFileProjectionRoot()
            .did()
            .name()
            .parentDids()
            .childDids()
            .ingressBytes()
            .dataSource()
            .stage()
            .parent()
            .flows()
            .name()
            .id()
            .type()
            .parent()
            .state()
            .parent()
            .created()
            .modified()
            .flowPlan()
            .name()
            .plugin()
            .name()
            .pluginVersion()
            .parent()
            .input()
            .metadata()
            .content()
            .mediaType()
            .name()
            .size()
            .segments()
            .did()
            .size()
            .offset()
            .uuid()
            .parent()
            .parent()
            .topics()
            .ancestorIds()
            .parent()
            .testMode()
            .testModeReason()
            .actions()
            .id()
            .type()
            .parent()
            .content()
            .mediaType()
            .name()
            .size()
            .segments()
            .did()
            .size()
            .offset()
            .uuid()
            .parent()
            .parent()
            .errorAcknowledged()
            .errorAcknowledgedReason()
            .nextAutoResume()
            .nextAutoResumeReason()
            .metadata()
            .name()
            .created()
            .modified()
            .errorCause()
            .errorContext()
            .filteredCause()
            .filteredContext()
            .state()
            .parent()
            .parent()
            .publishTopics()
            .depth()
            .parent()
            .egressed()
            .filtered();

    public static final ErrorSummaryByFlowProjectionRoot ERRORS_BY_FLOW_PROJECTION_ROOT =
            new ErrorSummaryByFlowProjectionRoot()
                    .count()
                    .offset()
                    .totalCount()
                    .countPerFlow()
                    .flow()
                    .count()
                    .dids()
                    .parent();

    public static final ErrorSummaryByMessageProjectionRoot ERRORS_BY_MESSAGE_PROJECTION_ROOT =
            new ErrorSummaryByMessageProjectionRoot()
                    .count()
                    .offset()
                    .totalCount()
                    .countPerMessage()
                    .message()
                    .flow()
                    .count()
                    .dids()
                    .parent();

    public static final FilteredSummaryByFlowProjectionRoot FILTERED_BY_FlOW_PROJECTION_ROOT =
            new FilteredSummaryByFlowProjectionRoot()
                    .count()
                    .offset()
                    .totalCount()
                    .countPerFlow()
                    .flow()
                    .count()
                    .dids()
                    .parent();

    public static final FilteredSummaryByMessageProjectionRoot FILTERED_BY_MESSAGE_PROJECTION_ROOT =
            new FilteredSummaryByMessageProjectionRoot()
                    .count()
                    .offset()
                    .totalCount()
                    .countPerMessage()
                    .message()
                    .flow()
                    .count()
                    .dids()
                    .parent();

}
