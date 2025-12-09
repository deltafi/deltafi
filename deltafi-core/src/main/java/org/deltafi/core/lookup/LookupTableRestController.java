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
package org.deltafi.core.lookup;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.security.NeedsPermission;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/lookup")
@Slf4j
public class LookupTableRestController {
    private final LookupTableService lookupTableService;

    public LookupTableRestController(@Nullable LookupTableService lookupTableService) {
        this.lookupTableService = lookupTableService;
    }

    @PostMapping(value = "/{lookupTableName}", consumes = MediaType.APPLICATION_JSON)
    @NeedsPermission.LookupTableUpdate
    public ResponseEntity<String> uploadTable(@PathVariable String lookupTableName,
            @RequestBody List<Map<String, String>> rows) throws LookupTableServiceException {
        log.info("Received JSON table for {}", lookupTableName);

        if (lookupTableService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Lookup table service disabled");
        }

        lookupTableService.updateTable(lookupTableName, rows);

        return ResponseEntity.ok(null);
    }

    @PostMapping(value = "/{lookupTableName}", consumes = "text/csv")
    @NeedsPermission.LookupTableUpdate
    public ResponseEntity<String> uploadTableFromCsv(@PathVariable String lookupTableName, @RequestBody Resource csv)
            throws LookupTableServiceException, IOException {
        log.info("Received CSV table for {}", lookupTableName);

        if (lookupTableService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Lookup table service disabled");
        }

        try (InputStream csvInputStream = csv.getInputStream()) {
            lookupTableService.updateTable(lookupTableName, csvInputStream);
        }

        return ResponseEntity.ok(null);
    }
}
