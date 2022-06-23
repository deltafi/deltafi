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
package org.deltafi.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.IngressService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

@Slf4j
@RequiredArgsConstructor
@RestController
public class IngressRest {
    private final IngressService ingressService;
    private final MetricRepository metricService;
    private final CoreAuditLogger coreAuditLogger;

    ObjectMapper objectMapper = new ObjectMapper();

    public static final String FILENAME_ATTRIBUTES = "flowfile.attributes";
    public static final String FILENAME_CONTENT = "flowfile.content";
    public static final String FLOWFILE_MEDIA_TYPE = "application/flowfile";
    public static final String FLOWFILE_V1_MEDIA_TYPE = "application/flowfile-v1";

    @NeedsPermission.DeltaFileIngress
    @PostMapping(value = "deltafile/ingress", consumes = MediaType.WILDCARD, produces = MediaType.TEXT_PLAIN)
    public ResponseEntity<String> ingressData(InputStream dataStream,
                                              @RequestHeader(value = "Filename", required = false) String filename,
                                              @RequestHeader(value = "Flow", required = false) String flow,
                                              @RequestHeader(value = "Metadata", required = false) String metadata,
                                              @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
                                              @RequestHeader(value = DeltaFiConstants.USER_HEADER, required = false, defaultValue = "system") String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        if (! ingressService.isEnabled()) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ingress disabled for this instance of DeltaFi",
                    flow, filename, contentType, username);
        }

        if (! ingressService.isStorageAvailable()) {
            return errorResponse(HttpStatus.INSUFFICIENT_STORAGE,
                    "Ingress temporarily disabled due to storage limits",
                    flow, filename, contentType, username);
        }

        log.debug("Ingressing: flow={} filename={} contentType={} username={}",
                flow,
                filename,
                contentType,
                username);

        try {
            IngressService.IngressResult ingressResult;
            if (contentType.equals(FLOWFILE_MEDIA_TYPE) || contentType.equals(FLOWFILE_V1_MEDIA_TYPE)) {
                ingressResult = ingressFlowfileV1(dataStream, metadata, flow, filename);
            } else {
                ingressResult = ingressBinary(dataStream, contentType, metadata, flow, filename);
            }

            coreAuditLogger.logIngress(username, ingressResult.filename);

            Map<String, String> tags = tagsFor(ingressResult.getFlow());
            metricService.increment(DeltaFiConstants.FILES_IN, tags, 1);
            metricService.increment(DeltaFiConstants.BYTES_IN, tags, ingressResult.getContentReference().getSize());

            return ResponseEntity.ok(ingressResult.getDid());
        } catch (IngressMetadataException exception) {
            log.error("Exception thrown: ", exception);
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), flow, filename, contentType, username);
        } catch (Throwable exception) {
            log.error("Exception thrown: ", exception);
            // includes IngressException and ObjectStorageException
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), flow, filename, contentType, username);
        }
    }

    ResponseEntity<String> errorResponse(HttpStatus code, String explanation, String flow, String filename, String contentType, String username) {
        log.error("{} error for flow={} filename={} contentType={} username={}: {}", code.value(), flow, filename, contentType, username, explanation);
        metricService.increment(DeltaFiConstants.FILES_DROPPED, tagsFor(flow), 1);
        return ResponseEntity.status(code).body(explanation);
    }

    private Map<String, String> tagsFor(String ingressFlow) {
        return MetricsUtil.tagsFor(ActionType.INGRESS.name(), INGRESS_ACTION, ingressFlow, null);
    }

    private IngressService.IngressResult ingressBinary(InputStream dataStream, String mediaType, String metadata, String flow, String filename) throws IngressMetadataException, IngressException, ObjectStorageException {
        if(Objects.isNull(filename)) throw new IngressMetadataException("Filename must be passed in as a header");
        return ingressService.ingressData(dataStream, filename, flow, metadata, mediaType);
    }

    static class FlowFile {
        byte[] content;
        Map<String, String> metadata;
    }

    private IngressService.IngressResult ingressFlowfileV1(InputStream dataStream, String metadataString, String flow, String filename) throws IngressMetadataException, IngressException, ObjectStorageException {
        FlowFile flowfile = unarchiveFlowfileV1(dataStream, fromJson(metadataString));
        if (flow == null) { flow = flowfile.metadata.get("flow"); }
        if (Objects.isNull(filename)) { filename = flowfile.metadata.get("filename"); }
        if(Objects.isNull(filename)) throw new IngressMetadataException("Filename must be passed in as a header or flowfile attribute");
        return ingressService.ingressData(new ByteArrayInputStream(flowfile.content), filename, flow, flowfile.metadata, MediaType.APPLICATION_OCTET_STREAM);
    }

    Map<String, String> fromJson(String metadata) throws IngressMetadataException {
        if (Objects.isNull(metadata)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IngressMetadataException("Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }

    FlowFile unarchiveFlowfileV1(@NotNull InputStream stream, Map<String, String> metadata) throws IngressException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {
            FlowFile flowfile = new FlowFile();
            flowfile.metadata = new HashMap<>(metadata);
            final TarArchiveEntry attribEntry = archive.getNextTarEntry();
            if (Objects.isNull(attribEntry)) { throw new IngressException("No content in flowfile"); }
            if (!attribEntry.getName().equals(FILENAME_ATTRIBUTES)) {
                throw new IngressException("Expected two tar entries: "
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
            throw new IngressException("Unable to unarchive tar", e);
        }
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile("<entry key=\"([^\"]+)\">([^<]+)</entry>", Pattern.MULTILINE);

    protected Map<String, String> extractFlowfileAttributes(final ArchiveInputStream stream) throws IOException {
        final Map<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        Matcher matcher = ENTRY_PATTERN.matcher(reader.lines().collect(Collectors.joining("\n")));
        while(matcher.find()) {
            final String escapedKey = matcher.group(1);
            final String escapedValue = matcher.group(2);

            result.put(StringEscapeUtils.unescapeXml(escapedKey), StringEscapeUtils.unescapeXml(escapedValue));
        }

        return result;
    }
}