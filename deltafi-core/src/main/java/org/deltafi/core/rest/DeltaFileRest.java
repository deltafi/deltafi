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
import org.deltafi.common.types.ImportResponse;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.exceptions.IngressRateLimitException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFileExporter;
import org.deltafi.core.services.DeltaFileImporter;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.ExportErrorsRequest;
import org.deltafi.core.types.IngressResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/deltafile")
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRest {
    private final CoreAuditLogger auditLogger;
    private final IngressService ingressService;
    private final DeltaFileExporter deltaFileExporter;
    private final DeltaFileImporter deltaFileImporter;

    @NeedsPermission.DeltaFileIngress
    @PostMapping(value = "ingress", consumes = MediaType.WILDCARD, produces = MediaType.TEXT_PLAIN)
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

    @NeedsPermission.DeltaFileExport
    @GetMapping(value = "export/{did}", produces = "application/x-tar")
    public ResponseEntity<StreamingResponseBody> exportDeltaFile(@PathVariable UUID did) {
        auditLogger.audit("exporting DeltaFile with a did of '{}'", did);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s.tar\"".formatted(did))
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(deltaFileExporter.exportDeltaFile(did));
    }

    @NeedsPermission.DeltaFileImport
    @PostMapping("import")
    public ResponseEntity<ImportResponse> importDeltaFiles(InputStream dataStream) {
        auditLogger.audit("importing DeltaFile(s)");
        return ResponseEntity.ok(deltaFileImporter.importDeltaFile(dataStream));
    }

    @NeedsPermission.DeltaFileExport
    @PostMapping(value = "export/errors", produces = "application/x-tar", consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity<StreamingResponseBody> exportErroredDeltaFiles(@RequestBody ExportErrorsRequest request) {
        auditLogger.audit("exporting up to {} errored DeltaFiles{}", request.getLimit(), request.isAcknowledge() ? " and acknowledged the errors" : "");
        String filename = String.format("errored_delta_files_%s.tar",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss-SSS")));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(filename))
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(deltaFileExporter.exportDeltaFiles(request));
    }
}
