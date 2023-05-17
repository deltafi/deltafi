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
package org.deltafi.core.converters;

import org.bson.Document;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.FormattedData;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeltaFileConverter implements AfterConvertCallback<DeltaFile> {

    private final MappingMongoConverter converter;

    public DeltaFileConverter(@Lazy MappingMongoConverter converter) {
        this.converter = converter;
    }

    @NotNull
    @Override
    public DeltaFile onAfterConvert(DeltaFile deltaFile, @NotNull Document document, @NotNull String collection) {
        if (deltaFile.getSchemaVersion() < 1) {
            // Perform conversion for older API version
            List<org.bson.Document> protocolStackDocuments = uncheckedGetList(document, "protocolStack");
            for (int i = 0; i < protocolStackDocuments.size(); i++) {
                org.bson.Document protocolLayerDocument = protocolStackDocuments.get(i);
                List<org.bson.Document> contentDocuments = uncheckedGetList(protocolLayerDocument, "content");
                for (int j = 0; j < contentDocuments.size(); j++) {
                    org.bson.Document contentDocument = contentDocuments.get(j);
                    org.bson.Document contentReferenceDocument = (org.bson.Document) contentDocument.get("contentReference");

                    if (contentReferenceDocument != null) {
                        String mediaType = contentReferenceDocument.getString("mediaType");
                        List<org.bson.Document> segments = uncheckedGetList(contentReferenceDocument, "segments");

                        Content content = deltaFile.getProtocolStack().get(i).getContent().get(j);
                        content.setMediaType(mediaType);
                        content.setSegments(convertDocumentListToSegmentList(segments));
                    }
                }
            }

            List<org.bson.Document> formattedDataDocuments = uncheckedGetList(document, "formattedData");
            for (int i = 0; i < formattedDataDocuments.size(); i++) {
                org.bson.Document formattedDataDocument = formattedDataDocuments.get(i);
                org.bson.Document contentReferenceDocument = (org.bson.Document) formattedDataDocument.get("contentReference");

                if (contentReferenceDocument != null) {
                    String name = formattedDataDocument.getString("filename");
                    String mediaType = contentReferenceDocument.getString("mediaType");
                    List<org.bson.Document> segments = uncheckedGetList(contentReferenceDocument, "segments");

                    FormattedData formattedData = deltaFile.getFormattedData().get(i);
                    Content content = new Content(name, mediaType, convertDocumentListToSegmentList(segments));
                    formattedData.setContent(content);
                }
            }
        }

        return deltaFile;
    }

    private List<Segment> convertDocumentListToSegmentList(List<Document> documentList) {
        return documentList.stream()
                .map(document -> converter.read(Segment.class, document))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> uncheckedGetList(Document document, String field) {
        if (document.containsKey(field)) {
            return (List<T>) document.get(field);
        } else {
            return Collections.emptyList();
        }
    }
}
