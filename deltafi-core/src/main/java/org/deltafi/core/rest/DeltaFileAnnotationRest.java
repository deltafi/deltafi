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
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFilesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DeltaFileAnnotationRest {

    private final DeltaFilesService deltaFilesService;
    private final CoreAuditLogger auditLogger;

    @NeedsPermission.DeltaFileMetadataWrite
    @PostMapping("/deltafile/annotate/{did}")
    public ResponseEntity<String> annotateDeltaFile(@PathVariable UUID did, @RequestParam Map<String, String> requestParams) {
        return annotateDeltaFile(did, requestParams, false);
    }

    @NeedsPermission.DeltaFileMetadataWrite
    @PostMapping("/deltafile/annotate/{did}/allowOverwrites")
    public ResponseEntity<String> annotateDeltaFileAllowOverwrites(@PathVariable UUID did, @RequestParam Map<String, String> requestParams) {
        return annotateDeltaFile(did, requestParams, true);
    }

    private ResponseEntity<String> annotateDeltaFile(UUID did, Map<String, String> requestParams, boolean allowOverwrites) {
        try {
            String annotations = requestParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
            auditLogger.audit("annotated deltafi with did {} with {}", did, annotations);
            deltaFilesService.addAnnotations(did, requestParams, allowOverwrites);
            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

}
