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
package org.deltafi.ingress.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.graphql.dgs.DeltafiGraphQLException;
import org.deltafi.common.metrics.MetricRepository;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.service.DeltaFileService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.deltafi.common.metrics.MetricsUtil.*;
import static org.deltafi.ingress.util.Metrics.tagsFor;

@Slf4j
@RequiredArgsConstructor
@RestController
public class DeltaFileRest {
    private final DeltaFileService deltaFileService;
    private final MetricRepository metricService;

    ObjectMapper objectMapper = new ObjectMapper();

    public static final String FILENAME_ATTRIBUTES = "flowfile.attributes";
    public static final String FILENAME_CONTENT = "flowfile.content";
    public static final String FLOWFILE_V1_MEDIA_TYPE = "application/flowfile";

    @PostMapping(value = "deltafile/ingress", consumes = MediaType.WILDCARD, produces = MediaType.TEXT_PLAIN)
    public ResponseEntity<String> ingressData(InputStream dataStream,
                                              @RequestHeader(value = "Filename", required = false) String filename,
                                              @RequestHeader(value = "Flow", required = false) String flow,
                                              @RequestHeader(value = "Metadata", required = false) String metadata,
                                              @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
                                              @RequestHeader(value = DeltaFiConstants.USER_HEADER, required = false) String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        log.debug("Ingressing: flow={} filename={} contentType={}",
                flow,
                filename,
                contentType);

        try {
            DeltaFileService.IngressResult ingressResult;
            if (contentType.equals(FLOWFILE_V1_MEDIA_TYPE)) {
                ingressResult = ingressFlowfileV1(dataStream, metadata, flow, filename, username);
            } else {
                ingressResult = ingressBinary(dataStream, contentType, metadata, flow, filename, username);
            }

            Map<String, String> tags = tagsFor(ingressResult.getFlow());
            metricService.increment(FILES_IN, tags, 1);
            metricService.increment(BYTES_IN, tags, ingressResult.getContentReference().getSize());

            return ResponseEntity.ok(ingressResult.getContentReference().getDid());
        } catch (ObjectStorageException | DeltafiGraphQLException | DeltafiException exception) {
            log.error("500 error", exception);
            metricService.increment(FILES_DROPPED, tagsFor(flow), 1);
            return ResponseEntity.status(500).body(exception.getMessage());
        } catch (DeltafiMetadataException exception) {
            metricService.increment(FILES_DROPPED, tagsFor(flow), 1);
            log.error("400 error", exception);
            return ResponseEntity.status(400).body(exception.getMessage());
        } catch (Throwable exception) {
            log.error("Unexpected error", exception);
            metricService.increment(FILES_DROPPED, tagsFor(flow), 1);
            return ResponseEntity.status(500).body(exception.getMessage());
        }
    }

    private DeltaFileService.IngressResult ingressBinary(InputStream dataStream, String mediaType, String metadata, String flow, String filename, String username) throws DeltafiMetadataException, DeltafiException, ObjectStorageException {
        if(Objects.isNull(filename)) throw new DeltafiMetadataException("Filename must be passed in as a header");
        return deltaFileService.ingressData(dataStream, filename, flow, metadata, mediaType, username);
    }

    static class FlowFile {
        byte[] content;
        Map<String, String> metadata;
    }

    private DeltaFileService.IngressResult ingressFlowfileV1(InputStream dataStream, String metadataString, String flow, String filename, String username) throws DeltafiMetadataException, DeltafiException, ObjectStorageException {
        FlowFile flowfile = unarchiveFlowfileV1(dataStream, fromJson(metadataString));
        if (flow == null) { flow = flowfile.metadata.get("flow"); }
        if (Objects.isNull(filename)) { filename = flowfile.metadata.get("filename"); }
        if(Objects.isNull(filename)) throw new DeltafiMetadataException("Filename must be passed in as a header or flowfile attribute");
        return deltaFileService.ingressData(new ByteArrayInputStream(flowfile.content), filename, flow, flowfile.metadata, MediaType.APPLICATION_OCTET_STREAM, username);
    }

    Map<String, String> fromJson(String metadata) throws DeltafiMetadataException {
        if (Objects.isNull(metadata)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new DeltafiMetadataException("Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }

    FlowFile unarchiveFlowfileV1(@NotNull InputStream stream, Map<String, String> metadata) throws DeltafiException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {
            FlowFile flowfile = new FlowFile();
            flowfile.metadata = new HashMap<>(metadata);
            final TarArchiveEntry attribEntry = archive.getNextTarEntry();
            if (Objects.isNull(attribEntry)) { throw new DeltafiException("No content in flowfile"); }
            if (!attribEntry.getName().equals(FILENAME_ATTRIBUTES)) {
                throw new DeltafiException("Expected two tar entries: "
                        + FILENAME_CONTENT + " and "
                        + FILENAME_ATTRIBUTES);
            }

            flowfile.metadata.putAll(extractFlowfileAttributes(archive));

            final TarArchiveEntry contentEntry = archive.getNextTarEntry();

            if (Objects.isNull(contentEntry) || !contentEntry.getName().equals(FILENAME_CONTENT)) {
                throw new IOException("Expected two tar entries: "
                        + FILENAME_CONTENT + " and "
                        + FILENAME_ATTRIBUTES);
            }

            flowfile.content = archive.readAllBytes();
            return flowfile;

        } catch (IOException e) {
            throw new DeltafiException("Unable to unarchive tar", e);
        }
    }

    protected Map<String, String> extractFlowfileAttributes(final ArchiveInputStream stream) throws IOException {
        final Properties props = new Properties();
        props.loadFromXML(CloseShieldInputStream.wrap(stream));

        final Map<String, String> result = new HashMap<>();
        for (final Map.Entry<Object, Object> entry : props.entrySet()) {
            final Object keyObject = entry.getKey();
            final Object valueObject = entry.getValue();
            if (!(keyObject instanceof String)) {
                throw new IOException("Flow file attributes object contains key of type "
                        + keyObject.getClass().getCanonicalName()
                        + " but expected java.lang.String");
            }
            final String key = (String) keyObject;
            final String value = (String) valueObject;
            result.put(key, value);
        }

        return result;
    }
}
