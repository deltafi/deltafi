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
package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.Coercing;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressInput;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.ingress.client.IngressGraphQLQuery;
import org.deltafi.ingress.client.IngressProjectionRoot;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeltaFileService {
    private final GraphQLClientService graphQLClientService;
    private final ContentStorageService contentStorageService;
    private final ObjectMapper objectMapper;

    private static final IngressProjectionRoot PROJECTION_ROOT = new IngressProjectionRoot().sourceInfo().flow().parent();
    private static final String FLOW_FIELD_PATH = "ingress.sourceInfo.flow";

    @RequiredArgsConstructor
    @Data
    static public class IngressResult {
        public final ContentReference contentReference;
        public final String flow;
    }

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String namespacedFlow, List<KeyValue> metadata, String mediaType, String username) throws ObjectStorageException, DeltafiException {
        String flow = (namespacedFlow == null) ? DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME : namespacedFlow;

        if (sourceFileName == null) throw new DeltafiException("filename required in source info");

        String did = UUID.randomUUID().toString();
        OffsetDateTime created = OffsetDateTime.now();

        ContentReference contentReference = contentStorageService.save(did, inputStream, mediaType);
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(contentReference).name(sourceFileName).build());

        try {
            flow = sendToIngressGraphQl(did, sourceFileName, flow, metadata, content, created, username);
        } catch (Exception e) {
            contentStorageService.delete(contentReference);
            throw e;
        }

        return new IngressResult(contentReference, flow);
    }

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String flow, Map<String, String> metadata, String mediaType, String username) throws ObjectStorageException, DeltafiException {
        return ingressData(inputStream, sourceFileName, flow, KeyValueConverter.fromMap(metadata), mediaType, username);
    }

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String flow, String metadataString, String mediaType, String username)
            throws ObjectStorageException, DeltafiException, DeltafiMetadataException {
        return ingressData(inputStream, sourceFileName, flow, fromMetadataString(metadataString), mediaType, username);
    }

    private KeyValue toKeyValueInput(Map.Entry<String, JsonNode> entry) {
        JsonNode node = entry.getValue();
        String value = node.isTextual() ? node.asText() : node.toString();
        return new KeyValue(entry.getKey(), value);
    }

    private String sendToIngressGraphQl(String did, String sourceFileName, String namespacedFlow, List<KeyValue> metadata,
                                      List<Content> content, OffsetDateTime created, String username) throws DeltafiException {
        IngressInput ingressInput = IngressInput.newBuilder()
                .did(did)
                .sourceInfo(new SourceInfo(sourceFileName, namespacedFlow, metadata))
                .content(content)
                .created(created)
                .build();

        GraphQLResponse response;
        try {
            response = graphQLClientService.graphQLClient(username).executeQuery(toGraphQlRequest(ingressInput).serialize());
        } catch (DeltafiGraphQLException e) {
            throw e;
        } catch (Exception e) {
            throw new DeltafiException(e.getMessage());
        }

        if (response.hasErrors()) {
            String errors = response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining(", "));
            throw new DeltafiGraphQLException(errors);
        }
        return response.extractValueAsObject(FLOW_FIELD_PATH, String.class);
    }

    List<KeyValue> fromMetadataString(String metadata) throws DeltafiMetadataException {
        if (Objects.isNull(metadata)) {
            return Collections.emptyList();
        }

        try {
            Map<String, JsonNode> keyValueMap = objectMapper.readValue(metadata, new TypeReference<>() {
            });
            return keyValueMap.entrySet().stream().map(this::toKeyValueInput).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new DeltafiMetadataException("Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }

    private GraphQLQueryRequest toGraphQlRequest(IngressInput ingressInput) {
        // Workaround for https://github.com/Netflix/dgs-codegen/issues/334. This will properly escape string values
        // containing special characters.
        Map<Class<?>, Coercing<?, ?>> scalars = Map.of(String.class, new GraphqlStringCoercing() {
            @Override
            public String serialize(Object input) {
                return net.minidev.json.JSONValue.escape((String) input);
            }
        });

        IngressGraphQLQuery ingressGraphQLQuery = IngressGraphQLQuery.newRequest().input(ingressInput).build();
        return new GraphQLQueryRequest(ingressGraphQLQuery, PROJECTION_ROOT, scalars);
    }

}
