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
package org.deltafi.core.datafetchers;

import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressEvent;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.generated.client.DeltaFileProjectionRoot;
import org.deltafi.core.generated.client.DeltaFilesProjectionRoot;
import org.deltafi.core.generated.client.ErrorSummaryByFlowProjectionRoot;
import org.deltafi.core.generated.client.ErrorSummaryByMessageProjectionRoot;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DeltaFilesDatafetcherTestHelper {

    static final String FILENAME = "theFilename";
    static final String FLOW = "theFlow";
    static final Long SIZE = 500L;
    static final String OBJECT_UUID = "theUuid";
    static final String OBJECT_UUID_2 = "theUuid2";
    static final String DID = UUID.randomUUID().toString();
    static final String DID_2 = UUID.randomUUID().toString();
    static final List<KeyValue> METADATA = Arrays.asList(new KeyValue("k1", "v1"), new KeyValue("k2", "v2"));
    static final String MEDIA_TYPE = "plain/text";
    static final ContentReference CONTENT_REFERENCE = new ContentReference(MEDIA_TYPE, new Segment(OBJECT_UUID, 0, SIZE, DID));
    static final List<Content> CONTENT = Collections.singletonList(Content.newBuilder().contentReference(CONTENT_REFERENCE).build());
    static final ContentReference CONTENT_REFERENCE_2 = new ContentReference(MEDIA_TYPE, new Segment(OBJECT_UUID_2, 0, SIZE, DID));
    static final List<Content> CONTENT_2 = Collections.singletonList(Content.newBuilder().contentReference(CONTENT_REFERENCE_2).build());
    static final SourceInfo SOURCE_INFO = new SourceInfo(FILENAME, FLOW, METADATA);
    static final SourceInfo SOURCE_INFO_EMPTY_METADATA = new SourceInfo(FILENAME, FLOW, Collections.emptyList());
    public static final IngressEvent INGRESS_INPUT_EMPTY_METADATA = new IngressEvent(DID, SOURCE_INFO_EMPTY_METADATA, CONTENT, OffsetDateTime.now());
    public static final IngressEvent INGRESS_INPUT = new IngressEvent(DID, SOURCE_INFO, CONTENT, OffsetDateTime.now());
    public static final IngressEvent INGRESS_INPUT_2 = new IngressEvent(DID_2, SOURCE_INFO, CONTENT_2, OffsetDateTime.now());

    public static final DeltaFilesProjectionRoot DELTA_FILES_PROJECTION_ROOT = new DeltaFilesProjectionRoot()
            .deltaFiles()
                .did()
                .parentDids()
                .childDids()
                .ingressBytes()
                .stage()
                .parent()
                .actions()
                .name()
                .created()
                .modified()
                .errorCause()
                .errorContext()
                .state()
                .parent()
                .parent()
                .protocolStack()
                .action()
                .content()
                .name()
                .metadata()
                .key()
                .value()
                .parent()
                .contentReference()
                .mediaType()
                .size()
                .segments()
                .uuid()
                .offset()
                .size()
                .did()
                .parent()
                .parent()
                .metadata()
                .key()
                .value()
                .parent()
                .parent()
                .parent()
                .sourceInfo()
                .filename()
                .flow()
                .metadata()
                .key()
                .value()
                .parent()
                .parent()
                .enrichment()
                .name()
                .value()
                .mediaType()
                .parent()
                .domains()
                .name()
                .value()
                .mediaType()
                .parent()
                .formattedData()
                .filename()
                .formatAction()
                .contentReference()
                .mediaType()
                .size()
                .segments()
                .uuid()
                .offset()
                .size()
                .did()
                .parent()
                .parent()
                .parent()
                .filtered()
                .egressed()
            .parent()
            .offset()
            .count()
            .totalCount();

    public static final DeltaFileProjectionRoot DELTA_FILE_PROJECTION_ROOT = new DeltaFileProjectionRoot()
            .did()
            .parentDids()
            .childDids()
            .ingressBytes()
            .stage()
                .parent()
            .actions()
                .name()
                .created()
                .queued()
                .start()
                .stop()
                .modified()
                .errorCause()
                .errorContext()
                .state()
                    .parent()
                .parent()
            .protocolStack()
              .action()
              .content()
                .name()
                .metadata()
                  .key()
                  .value()
                  .parent()
                .contentReference()
                  .mediaType()
                  .size()
                  .segments()
                    .uuid()
                    .offset()
                    .size()
                    .did()
                    .parent()
                  .parent()
                .parent()
              .metadata()
                .key()
                .value()
                .parent()
              .parent()
            .sourceInfo()
              .filename()
              .flow()
              .metadata()
                .key()
                .value()
                .parent()
              .parent()
            .enrichment()
                .name()
                .value()
                .mediaType()
                .parent()
            .domains()
                .name()
                .value()
                .mediaType()
                .parent()
            .formattedData()
                .filename()
                .formatAction()
                .contentReference()
                    .mediaType()
                    .size()
                    .segments()
                        .uuid()
                        .offset()
                        .size()
                        .did()
                        .parent()
                    .parent()
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

}
