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
package org.deltafi.core.rest.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;

@RestControllerAdvice(basePackages = "org.deltafi.core.rest")
@Slf4j
public class RestExceptionHandler {
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e, HandlerMethod hm) throws IOException {
        if (!e.getMessage().contains("Connection reset by peer")) {
            throw e;
        }
        String ctrl = hm.getBeanType().getSimpleName();
        if (log.isDebugEnabled()) {
            log.debug("{} endpoint: Connection reset by peer", ctrl, e);
        } else {
            log.warn("{} endpoint: Connection reset by peer", ctrl);
        }
    }

    /**
     * Handle the benign “async race” where Spring Security tries to create/save
     * an HTTP session after the response is already committed (common in SSE,
     * DeferredResult, etc.). Suppress it rather than polluting the ERROR log.
     */
    @ExceptionHandler(IllegalStateException.class)
    public void handleIllegalStateException(IllegalStateException e, HandlerMethod hm) {
        // only swallow the “response committed” case
        if (!e.getMessage().contains("Cannot create a session after the response has been committed")) {
            throw e;
        }
        String ctrl = hm.getBeanType().getSimpleName();
        if (log.isDebugEnabled()) {
            log.debug("Async race in {}: response already committed, skipping session save", ctrl, e);
        } else {
            log.warn("Async race in {}: response already committed, skipping session save", ctrl);
        }
    }
}
