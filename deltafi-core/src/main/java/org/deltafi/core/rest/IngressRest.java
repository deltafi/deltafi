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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.exceptions.IngressRateLimitException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.IngressResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/deltafile/ingress")
@RequiredArgsConstructor
@Slf4j
public class IngressRest {
    private final IngressService ingressService;

    @NeedsPermission.DeltaFileIngress
    @PostMapping(consumes = MediaType.WILDCARD, produces = MediaType.TEXT_PLAIN)
    public ResponseEntity<String> ingressData(InputStream dataStream,
            @RequestHeader(value = "Filename", required = false) String filename,
            @RequestHeader(value = "DataSource", required = false) String dataSource,
            @RequestHeader(value = "Metadata", required = false) String metadata,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestHeader(value = DeltaFiConstants.USER_NAME_HEADER, defaultValue = "system") String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        try {
            List<IngressResult> ingressResults = ingressService.ingress(dataSource, filename, contentType, username, metadata,
                    dataStream, OffsetDateTime.now());
            return ResponseEntity.ok(String.join(",", ingressResults.stream().map(r -> r.did().toString()).toList()));
        } catch (IngressUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (IngressRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (IngressStorageException | ObjectStorageException e) {
            return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body(e.getMessage());
        } catch (IngressMetadataException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Throwable e) { // includes IngressException
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
