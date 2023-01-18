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
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.security.NeedsPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Map;

import static org.deltafi.common.constant.DeltaFiConstants.SURVEY_ACTION;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SurveyRest {
    private final MetricRepository metricService;
    private final CoreAuditLogger coreAuditLogger;

    @NeedsPermission.DeltaFileIngress
    @PostMapping(value = "survey", consumes = MediaType.WILDCARD)
    public ResponseEntity<String> survey(@QueryParam(value = "flow") String flow,
                                         @QueryParam(value = "bytes") Long bytes,
                                         @QueryParam(value = "count") Long count,
                                         @RequestHeader(value = DeltaFiConstants.USER_HEADER, required = false, defaultValue = "system") String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        if (flow == null) {
            log.error("Received survey request from {} with no flow specified", username);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing flow parameter");
        }

        if (bytes == null) {
            bytes = 0L;
        }

        if (count == null) {
            count = 1L;
        }

        log.debug("Survey: flow={} bytes={} count={} username={}",
                flow,
                bytes,
                count,
                username);
        try {
            coreAuditLogger.logSurvey(username, flow, bytes, count);
            Map<String, String> tags = MetricsUtil.tagsFor(ActionType.INGRESS.name(), SURVEY_ACTION, flow, null);
            metricService.increment(DeltaFiConstants.FILES_IN, tags, count);
            metricService.increment(DeltaFiConstants.BYTES_IN, tags, bytes);

            return ResponseEntity.ok(null);
        } catch (Throwable exception) {
            log.error("Exception thrown: ", exception);
            log.error("{} error for flow={} bytes={} count={} username={}: {}", HttpStatus.INTERNAL_SERVER_ERROR.value(), flow, bytes, count, username, exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
        }
    }
}
