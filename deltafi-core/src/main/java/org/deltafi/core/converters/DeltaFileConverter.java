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
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        // Version 1 - Flatten content
        // protocol stack entries will be moved to actions with v3 upconvert, so update the document
        // for formattedData, update the deltaFile
        if (deltaFile.getSchemaVersion() < 1) {
            List<org.bson.Document> protocolStackDocuments = uncheckedGetList(document, "protocolStack");
            for (Document protocolLayerDocument : protocolStackDocuments) {
                List<Document> contentDocuments = uncheckedGetList(protocolLayerDocument, "content");
                for (Document contentDocument : contentDocuments) {
                    Document contentReferenceDocument = (Document) contentDocument.get("contentReference");

                    if (contentReferenceDocument != null) {
                        String mediaType = contentReferenceDocument.getString("mediaType");
                        List<Document> segments = uncheckedGetList(contentReferenceDocument, "segments");
                        contentDocument.append("mediaType", mediaType);
                        contentDocument.append("segments", segments);
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

        // Version 2 - Rename indexedMetadata to annotations
        if (deltaFile.getSchemaVersion() < 2) {
            if (document.containsKey("indexedMetadata")) {
                deltaFile.setAnnotations(uncheckedGetMap(document,"indexedMetadata"));
                deltaFile.setAnnotationKeys(new HashSet<>(uncheckedGetList(document, "indexedMetadataKeys")));
            }
        }

        // Version 3 - merge protocolStack into actions and copy sourceInfo metadata to IngressAction

        if (deltaFile.getSchemaVersion() < 3) {
            List<org.bson.Document> protocolStackDocuments = uncheckedGetList(document, "protocolStack");
            for (Document protocolLayerDocument : protocolStackDocuments) {
                String actionName = protocolLayerDocument.getString("action");

                // find the last action with the matching name
                Action action = null;
                for (int j = deltaFile.getActions().size() - 1; j >= 0; j--) {
                    Action currAction = deltaFile.getActions().get(j);
                    if (currAction.getName().equals(actionName)) {
                        action = currAction;
                        break;
                    }
                }

                if (action != null) {
                    List<Document> content = uncheckedGetList(protocolLayerDocument, "content");
                    if (content.size() > 0) {
                        action.setContent(convertDocumentListToContentList(content));
                    }
                    if (protocolLayerDocument.containsKey("metadata")) {
                        Map<String, String> metadata = uncheckedGetMap(protocolLayerDocument, "metadata");
                        action.setMetadata(metadata);
                    }
                    if (protocolLayerDocument.containsKey("deleteMetadataKeys")) {
                        List<String> deleteMetadataKeys = uncheckedGetList(protocolLayerDocument, "deleteMetadataKeys");
                        action.setDeleteMetadataKeys(deleteMetadataKeys);
                    }
                }
            }

            if (!deltaFile.getActions().isEmpty() && deltaFile.getSourceInfo() != null) {
                deltaFile.getActions().get(0).setMetadata(deltaFile.getSourceInfo().getMetadata());
            }

            for (Action action : deltaFile.getActions()) {
                if (action.getType() == null || action.getType() == ActionType.UNKNOWN) {
                    if (action.getName().equals("IngressAction")) {
                        action.setType(ActionType.INGRESS);
                    } else if (action.getName().toLowerCase().contains("transform")) {
                        action.setType(ActionType.TRANSFORM);
                    } else if (action.getName().toLowerCase().contains("load")) {
                        action.setType(ActionType.LOAD);
                    } else if (action.getName().toLowerCase().contains("format")) {
                        action.setType(ActionType.FORMAT);
                    } else if (action.getName().toLowerCase().contains("domain")) {
                        action.setType(ActionType.DOMAIN);
                    } else if (action.getName().toLowerCase().contains("enrich")) {
                        action.setType(ActionType.ENRICH);
                    } else if (action.getName().toLowerCase().contains("validate")) {
                        action.setType(ActionType.VALIDATE);
                    } else if (action.getName().toLowerCase().contains("egress")) {
                        action.setType(ActionType.EGRESS);
                    } else {
                        action.setType(ActionType.UNKNOWN);
                    }
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

    private List<Content> convertDocumentListToContentList(List<Document> documentList) {
        return documentList.stream()
                .map(document -> converter.read(Content.class, document))
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

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private <X, Y> Map<X, Y> uncheckedGetMap(Document document, String field) {
        if (document.containsKey(field)) {
            return (Map<X, Y>) document.get(field);
        } else {
            return Collections.emptyMap();
        }
    }
}
