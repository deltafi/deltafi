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
package org.deltafi.core.domain.datafetchers;

import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;

import java.time.OffsetDateTime;
import java.util.*;

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
    static final ContentReference CONTENT_REFERENCE = new ContentReference(OBJECT_UUID, 0, SIZE, DID, MEDIA_TYPE);
    static final List<Content> CONTENT = Collections.singletonList(Content.newBuilder().contentReference(CONTENT_REFERENCE).build());
    static final ContentReference CONTENT_REFERENCE_2 = new ContentReference(OBJECT_UUID_2, 0, SIZE, DID, MEDIA_TYPE);
    static final List<Content> CONTENT_2 = Collections.singletonList(Content.newBuilder().contentReference(CONTENT_REFERENCE_2).build());
    static final SourceInfo SOURCE_INFO = new SourceInfo(FILENAME, FLOW, METADATA);
    static final SourceInfo SOURCE_INFO_EMPTY_METADATA = new SourceInfo(FILENAME, FLOW, Collections.emptyList());
    public static final IngressInput INGRESS_INPUT_EMPTY_METADATA = new IngressInput(DID, SOURCE_INFO_EMPTY_METADATA, CONTENT, OffsetDateTime.now());
    public static final IngressInput INGRESS_INPUT = new IngressInput(DID, SOURCE_INFO, CONTENT, OffsetDateTime.now());
    public static final IngressInput INGRESS_INPUT_2 = new IngressInput(DID_2, SOURCE_INFO, CONTENT_2, OffsetDateTime.now());

    public static final DeltaFilesProjectionRoot DELTA_FILES_PROJECTION_ROOT = new DeltaFilesProjectionRoot()
            .deltaFiles()
                .did()
                .parentDids()
                .childDids()
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
                .type()
                .action()
                .content()
                .name()
                .metadata()
                .key()
                .value()
                .parent()
                .contentReference()
                .uuid()
                .offset()
                .size()
                .did()
                .mediaType()
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
                .uuid()
                .offset()
                .size()
                .did()
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
              .type()
              .action()
              .content()
                .name()
                .metadata()
                  .key()
                  .value()
                  .parent()
                .contentReference()
                  .uuid()
                  .offset()
                  .size()
                  .did()
                  .mediaType()
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
                    .uuid()
                    .offset()
                    .size()
                    .did()
                    .parent()
                .parent()
            .egressed()
            .filtered();
}
