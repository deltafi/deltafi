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
package org.deltafi.core.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.security.NeedsPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SurveyRest {
    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;

    @NeedsPermission.DeltaFileIngress
    @PostMapping(value = "survey", consumes = MediaType.WILDCARD)
    @Deprecated
    public ResponseEntity<String> survey(@QueryParam(value = "flow") String flow,
                                         @QueryParam(value = "bytes") Long bytes,
                                         @QueryParam(value = "files") Long files,
                                         @QueryParam(value = "subflow") String subflow,
                                         @QueryParam(value = "direction") String direction,
                                         @RequestHeader(value = DeltaFiConstants.USER_HEADER, required = false, defaultValue = "system") String username) {
        username = StringUtils.isNotBlank(username) ? username : "system";

        if (flow == null) {
            log.error("Received survey request from {} with no flow specified", username);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header("Deprecated", "True").build();
        }

        if (direction == null) direction = "none";
        if (bytes == null) bytes = 0L;
        if (files == null) files = 1L;

        log.debug("Survey: flow={} subflow={} direction={} bytes={} files={} username={}",
                flow,
                subflow,
                direction,
                bytes,
                files,
                username);
        try {
            coreAuditLogger.logSurvey(username, flow, subflow, direction, bytes, files);

            Map<String, String> tags = new HashMap<>();
            tags.put("surveyFlow", flow);
            tags.put("surveyDirection", direction);
            metricService.increment(DeltaFiConstants.SURVEY_FILES, tags, files);
            metricService.increment(DeltaFiConstants.SURVEY_BYTES, tags, bytes);

            if (subflow != null && !subflow.isBlank()) {
                Map<String, String> subflowTags = new HashMap<>(tags);
                subflowTags.put("surveySubflow", subflow);
                metricService.increment(DeltaFiConstants.SURVEY_SUBFLOW_FILES, subflowTags, files);
                metricService.increment(DeltaFiConstants.SURVEY_SUBFLOW_BYTES, subflowTags, bytes);
            }


            return ResponseEntity.status(HttpStatus.OK).header("Deprecated", "True").build();
        } catch (Throwable exception) {
            log.error("Exception thrown: ", exception);
            log.error("{} error for flow={} subflow={} direction={} bytes={} files={} username={}: {}", HttpStatus.INTERNAL_SERVER_ERROR.value(), flow, subflow, direction, bytes, files, username, exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
        }
    }
}
