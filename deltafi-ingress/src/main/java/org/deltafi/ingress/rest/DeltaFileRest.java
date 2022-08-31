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
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.service.DeltaFileService;
import org.deltafi.ingress.service.MetricService;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

@Slf4j
@Path("deltafile")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DeltaFileRest {
    private final DeltaFileService deltaFileService;
    private final MetricService metricService;

    ObjectMapper objectMapper = new ObjectMapper();

    public static final String FILENAME_ATTRIBUTES = "flowfile.attributes";
    public static final String FILENAME_CONTENT = "flowfile.content";
    public static final String FLOWFILE_V1_MEDIA_TYPE = "application/flowfile";

    @POST
    @Path("ingress")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response ingressData(InputStream dataStream, @Context HttpHeaders headers,
                                @QueryParam("filename") String filenameFromQueryParam,
                                @QueryParam("flow") String flowFromQueryParam,
                                @HeaderParam("Filename") String filenameFromHeader,
                                @HeaderParam("Flow") String flowFromHeader,
                                @HeaderParam("Metadata") String metadata) {
        String mediaType = headers.getMediaType().getType() + "/" + headers.getMediaType().getSubtype();
        String flow = Objects.nonNull(flowFromQueryParam) ? flowFromQueryParam : flowFromHeader;
        String filename = Objects.nonNull(filenameFromQueryParam) ? filenameFromQueryParam : filenameFromHeader;

        String username = headers.getHeaderString(DeltaFiConstants.USER_HEADER);
        username = (username != null && !username.isBlank()) ? username : "system";

        log.debug("Ingressing: flow={} filename={} mediaType={}",
                flow,
                filename,
                mediaType);

        try {
            DeltaFileService.IngressResult ingressResult;
            if (mediaType.equals(FLOWFILE_V1_MEDIA_TYPE)) {
                ingressResult = ingressFlowfileV1(dataStream, metadata, flow, filename, username);
            } else {
                ingressResult = ingressBinary(dataStream, mediaType, metadata, flow, filename, username);
            }

            Map<String, String> tags = tagsFor(ingressResult.getFlow());
            metricService.increment("files_in", tags, 1);
            metricService.increment("bytes_in", tags, ingressResult.getContentReference().getSize());

            return Response.ok(ingressResult.getContentReference().getDid()).build();
        } catch (ObjectStorageException | DeltafiGraphQLException | DeltafiException exception) {
            log.error("500 error", exception);
            metricService.increment("files_dropped", tagsFor(flow), 1);
            return Response.status(500).entity(exception.getMessage()).build();
        } catch (DeltafiMetadataException exception) {
            log.error("400 error", exception);
            metricService.increment("files_dropped", tagsFor(flow), 1);
            return Response.status(400).entity(exception.getMessage()).build();
        } catch (Throwable exception) {
            log.error("Unexpected error", exception);
            metricService.increment("files_dropped", tagsFor(flow), 1);
            return Response.status(500).entity(exception.getMessage()).build();
        }
    }

    private DeltaFileService.IngressResult ingressBinary(InputStream dataStream, String mediaType, String metadata, String flow, String filename, String username) throws DeltafiMetadataException, DeltafiException, ObjectStorageException {
        if(Objects.isNull(filename)) throw new DeltafiMetadataException("filename must be passed in as a query parameter or header");
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
        if(Objects.isNull(filename)) throw new DeltafiMetadataException("filename must be passed in as a query parameter, header, or flowfile attribute");
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

    private Map<String, String> tagsFor(String flow) {
        if (flow == null) flow = "unknown";
        return Map.of("action", INGRESS_ACTION,
                "ingressFlow", flow,
                "source", "ingress");
    }



}
