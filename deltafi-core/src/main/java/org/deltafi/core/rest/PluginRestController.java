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
import org.deltafi.common.types.PluginRegistration;
import org.deltafi.core.integration.IntegrationService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;

@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
@Slf4j
public class PluginRestController {
    private final PluginService pluginService;
    private final IntegrationService integrationService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity<String> createPlugin(@RequestBody PluginRegistration pluginRegistration) {
        log.info("Received plugin registration for {}", pluginRegistration.getPluginCoordinates());
        pluginService.acquireUpdateLock();

        try {
            Result result = pluginService.register(pluginRegistration, integrationService);

            pluginService.FlushToDB();

            if (result.isSuccess()) {
                try {
                    pluginService.revalidateFlows();
                } catch (Exception ignored) {
                    // log an error if anything goes wrong revalidating flows, but do not prevent a successful registration
                    log.error("Problem");
                }
            } else {
                return ResponseEntity.badRequest().body(String.join("\n", result.getErrors()));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Plugin registration error: %s. See core logs for more details".formatted(e.getMessage()));
        } finally {
            pluginService.releaseUpdateLock();
        }

        return ResponseEntity.ok(null);
    }
}
