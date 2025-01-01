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

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.ServerSentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/sse")
@AllArgsConstructor
public class ServerSentEventRest {
    private final ServerSentService serverSentService;

    @NeedsPermission.UIAccess
    @GetMapping(produces = "text/event-stream")
    public SseEmitter sse(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        return serverSentService.createSseEmitter();
    }
}
