/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.FetchContentService;
import org.deltafi.core.types.ContentRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("content")
@AllArgsConstructor
public class ContentRest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    static final String BASE64_DECODE_ERROR = "Failed to decode base64 encoded json";
    static final String JSON_PARSE_ERROR = "Failed to parse content object from json";

    private final FetchContentService fetchContentService;

    @NeedsPermission.DeltaFileContentView
    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> content(@RequestParam String content) throws ObjectStorageException {
        return buildResponse(fromEncodedJson(content));
    }

    @NeedsPermission.DeltaFileContentView
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> content(@RequestBody ContentRequest content) throws ObjectStorageException {
        return buildResponse(content);
    }

    private ResponseEntity<InputStreamResource> buildResponse(ContentRequest contentRequest) throws ObjectStorageException {
        String filename = contentRequest.name();
        if (StringUtils.isBlank(filename)) {
            filename = "content";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\";");
        headers.add("Content-Transfer-Encoding", "binary");
        headers.add("Cache-Control", "no-cache");
        headers.add("Content-Type", contentRequest.mediaType());
        headers.add("Content-Length", "" + contentRequest.size());

        // note fetchContentService handles auditing access to the content
        InputStreamResource body = new InputStreamResource(fetchContentService.streamContent(contentRequest));
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private ContentRequest fromEncodedJson(String encodedContent) {
        try {
            return MAPPER.readValue(Base64.getDecoder().decode(encodedContent), ContentRequest.class);
        } catch (IllegalArgumentException e) {
            log.error(BASE64_DECODE_ERROR, e);
            throw new IllegalArgumentException(BASE64_DECODE_ERROR);
        } catch (IOException e) {
            log.error(JSON_PARSE_ERROR, e);
            throw new IllegalArgumentException(JSON_PARSE_ERROR);
        }
    }

    @ExceptionHandler(EntityNotFound.class)
    public ResponseEntity<Error> handleNotFound(EntityNotFound ex) {
        return handleFailure(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ObjectStorageException.class)
    public ResponseEntity<Error> handleObjectStorageException(ObjectStorageException ex) {
        return handleFailure(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Error> handleBadRequest(IllegalArgumentException ex) {
        return handleFailure(HttpStatus.BAD_REQUEST, ex);
    }

    public ResponseEntity<Error> handleFailure(HttpStatus status, Exception ex) {
        return ResponseEntity.status(status).body(new Error(ex.getMessage()));
    }

    public record Error(String error, OffsetDateTime timestamp) {
        public Error(String error) {
            this(error, OffsetDateTime.now());
        }
    }
}
