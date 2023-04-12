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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DiskSpaceService;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.IngressResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Map;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

@Slf4j
@RequiredArgsConstructor
@RestController
public class IngressRest {
    private final IngressService ingressService;
    private final MetricRepository metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DiskSpaceService diskSpaceService;

    @NeedsPermission.DeltaFileIngress
    @PostMapping(value = "deltafile/ingress", consumes = MediaType.WILDCARD, produces = MediaType.TEXT_PLAIN)
    public ResponseEntity<String> ingressData(InputStream dataStream,
                                              @RequestHeader(value = "Filename", required = false) String filename,
                                              @RequestHeader(value = "Flow", required = false) String flow,
                                              @RequestHeader(value = "Metadata", required = false) String metadata,
                                              @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
                                              @RequestHeader(value = DeltaFiConstants.USER_HEADER, required = false, defaultValue = "system") String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        if (!ingressService.isEnabled()) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ingress disabled for this instance of DeltaFi",
                    flow, filename, contentType, username);
        }

        if (diskSpaceService.isContentStorageDepleted()) {
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
            IngressResult ingressResult = ingressService.ingressData(dataStream, filename, flow, metadata, contentType);

            coreAuditLogger.logIngress(username, ingressResult.filename());

            Map<String, String> tags = tagsFor(ingressResult.flow());
            metricService.increment(DeltaFiConstants.FILES_IN, tags, 1);
            metricService.increment(DeltaFiConstants.BYTES_IN, tags, ingressResult.contentReference().getSize());

            return ResponseEntity.ok(ingressResult.did());
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

}
