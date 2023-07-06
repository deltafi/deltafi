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
package org.deltafi.core.converters;

import org.bson.Document;
import org.deltafi.common.types.*;
import org.deltafi.core.services.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.TransformFlow;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

@Component
public class DeltaFileConverter implements AfterConvertCallback<DeltaFile> {

    private final MappingMongoConverter converter;
    private final TransformFlowService transformFlowService;
    private final IngressFlowService ingressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;

    public DeltaFileConverter(@Lazy MappingMongoConverter converter,
                              @Lazy TransformFlowService transformFlowService,
                              @Lazy IngressFlowService ingressFlowService,
                              @Lazy EnrichFlowService enrichFlowService,
                              @Lazy EgressFlowService egressFlowService) {
        this.converter = converter;
        this.transformFlowService = transformFlowService;
        this.ingressFlowService = ingressFlowService;
        this.enrichFlowService = enrichFlowService;
        this.egressFlowService = egressFlowService;
    }

    @NotNull
    @Override
    public DeltaFile onAfterConvert(DeltaFile deltaFile, @NotNull Document document, @NotNull String collection) {
        // Version 1 - Flatten content
        if (deltaFile.getSchemaVersion() < 1) {
            updateToV1(document);
        }

        // Version 2 - Rename indexedMetadata to annotations
        if (deltaFile.getSchemaVersion() < 2) {
            updateToV2(deltaFile, document);
        }

        // Version 3 - merge protocolStack into actions, add action types, and copy sourceInfo metadata to IngressAction
        if (deltaFile.getSchemaVersion() < 3) {
            updateToV3(deltaFile, document);
        }

        // Version 4 - merge formattedData into actions and add action flows
        if (deltaFile.getSchemaVersion() < 4) {
            updateToV4(deltaFile, document);
        }

        // Version 5 - rename enrichment to enrichments
        // Version 6 - move domains and enrichments to actions
        if (deltaFile.getSchemaVersion() < 6) {
            updateToV6(deltaFile, document);
        }

        return deltaFile;
    }

    private void updateToV1(Document document) {
        // protocol stack entries will be moved to actions with v3 up-convert, so update the document instead of the deltaFile
        List<Document> protocolStackDocuments = uncheckedGetList(document, "protocolStack");
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

        // formatted data entries will be moved to actions with v4 up-convert, so update the document instead of the deltaFile
        List<Document> formattedDataDocuments = uncheckedGetList(document, "formattedData");
        for (Document formattedDataDocument : formattedDataDocuments) {
            Document contentReferenceDocument = (Document) formattedDataDocument.get("contentReference");

            if (contentReferenceDocument != null) {
                Document contentDocument = new Document();
                contentDocument.append("name", formattedDataDocument.getString("filename"));
                contentDocument.append("mediaType", contentReferenceDocument.getString("mediaType"));
                List<Document> segments = uncheckedGetList(contentReferenceDocument, "segments");
                contentDocument.append("segments", segments);
                formattedDataDocument.append("content", contentDocument);
            }
        }
    }

    private void updateToV2(DeltaFile deltaFile, Document document) {
        if (document.containsKey("indexedMetadata")) {
            deltaFile.setAnnotations(uncheckedGetMap(document, "indexedMetadata"));
            deltaFile.setAnnotationKeys(new HashSet<>(uncheckedGetList(document, "indexedMetadataKeys")));
        }
    }

    private void updateToV3(DeltaFile deltaFile, Document document) {
        List<Document> protocolStackDocuments = uncheckedGetList(document, "protocolStack");
        for (Document protocolLayerDocument : protocolStackDocuments) {
            String actionName = protocolLayerDocument.getString("action");
            Action action = deltaFile.lastAction(actionName);

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
                if (action.getName().equals(INGRESS_ACTION)) {
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
                    String flowName = action.getName().split("\\.")[0];
                    if (transformFlowService.hasFlow(flowName)) {
                        TransformFlow flow = transformFlowService.getFlowOrThrow(flowName);
                        if (flow.getEgressAction().getName().equals(action.getName())) {
                            action.setType(ActionType.EGRESS);
                        } else if (flow.getTransformActions().stream().map(DeltaFiConfiguration::getName).toList().contains(action.getName())) {
                            action.setType(ActionType.TRANSFORM);
                        }
                    } else if (ingressFlowService.hasFlow(flowName)) {
                        IngressFlow flow = ingressFlowService.getFlowOrThrow(flowName);
                        if (flow.getLoadAction().getName().equals(action.getName())) {
                            action.setType(ActionType.LOAD);
                        } else if (flow.getTransformActions().stream().map(DeltaFiConfiguration::getName).toList().contains(action.getName())) {
                            action.setType(ActionType.TRANSFORM);
                        }
                    } else if (enrichFlowService.hasFlow(flowName)) {
                        EnrichFlow flow = enrichFlowService.getFlowOrThrow(flowName);
                        if (flow.getDomainActions().stream().map(DeltaFiConfiguration::getName).toList().contains(action.getName())) {
                            action.setType(ActionType.DOMAIN);
                        } else if (flow.getEnrichActions().stream().map(DeltaFiConfiguration::getName).toList().contains(action.getName())) {
                            action.setType(ActionType.ENRICH);
                        }
                    } else if (egressFlowService.hasFlow(flowName)) {
                        EgressFlow flow = egressFlowService.getFlowOrThrow(flowName);
                        if (flow.getFormatAction().getName().equals(action.getName())) {
                            action.setType(ActionType.FORMAT);
                        } else if (flow.getValidateActions().stream().map(DeltaFiConfiguration::getName).toList().contains(action.getName())) {
                            action.setType(ActionType.VALIDATE);
                        } else if (flow.getEgressAction().getName().equals(action.getName())) {
                            action.setType(ActionType.EGRESS);
                        }
                    }

                    if (action.getType() == null) {
                        action.setType(ActionType.UNKNOWN);
                    }
                }
            }
        }
    }

    private void updateToV4(DeltaFile deltaFile, Document document) {
        List<Document> formattedDataDocuments = uncheckedGetList(document, "formattedData");
        for (Document formattedDataDocument : formattedDataDocuments) {
            String actionName = formattedDataDocument.getString("formatAction");
            Action action = deltaFile.lastAction(actionName);

            if (action != null) {
                if (formattedDataDocument.containsKey("content")) {
                    Document contentDocument = (Document) formattedDataDocument.get("content");
                    Content content = converter.read(Content.class, contentDocument);
                    action.setContent(List.of(content));
                }
                if (formattedDataDocument.containsKey("metadata")) {
                    Map<String, String> metadata = uncheckedGetMap(formattedDataDocument, "metadata");
                    action.setMetadata(metadata);
                }
            }
        }

        for (Action action : deltaFile.getActions()) {
            if (action.getFlow() == null) {
                if (action.getName().equals(INGRESS_ACTION)) {
                    action.setFlow("");
                } else {
                    action.setFlow(action.getName().split("\\.")[0]);
                }
            }
        }
    }

    private void updateToV6(DeltaFile deltaFile, Document document) {
        List<Document> enrichments = new ArrayList<>(uncheckedGetList(document, "enrichments"));
        enrichments.addAll(uncheckedGetList(document, "enrichment"));

        if (!enrichments.isEmpty()) {
            Action enrichAction = deltaFile.getActions().stream().filter(a -> a.getType() == ActionType.ENRICH).findFirst().orElse(null);
            if (enrichAction != null) {
                for (Document enrichment : enrichments) {
                    enrichAction.getEnrichments().add(Enrichment.builder()
                            .name(enrichment.getString("name"))
                            .mediaType(enrichment.getString("mediaType"))
                            .value(enrichment.getString("value"))
                            .build());
                }
            }
        }

        List<Document> domains = uncheckedGetList(document, "domains");

        if (!domains.isEmpty()) {
            Action loadAction = deltaFile.getActions().stream().filter(a -> a.getType() == ActionType.LOAD).findFirst().orElse(null);
            if (loadAction != null) {
                for (Document domain : domains) {
                    loadAction.getDomains().add(Domain.builder()
                            .name(domain.getString("name"))
                            .mediaType(domain.getString("mediaType"))
                            .value(domain.getString("value"))
                            .build());
                }
            }
        }
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
