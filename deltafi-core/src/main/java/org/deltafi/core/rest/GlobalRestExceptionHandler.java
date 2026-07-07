/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.types.ErrorHolder;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.types.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    // The container-enforced multipart cap, bound once at startup (see spring.servlet.multipart.max-file-size).
    // e.getMaxUploadSize() is unreliable with the standard resolver (returns -1), so report the configured value.
    @Value("${spring.servlet.multipart.max-file-size:5GB}")
    private String maxUploadSize;

    @ExceptionHandler(InvalidEntityException.class)
    public ErrorHolder handleInvalidEntityException(InvalidEntityException e, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return e.getErrorHolder();
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ErrorResponse handleInvalidUpdateException(InvalidRequestException e, HttpServletResponse response) {
        response.setStatus(e.getStatusCode());
        return new ErrorResponse(e.getMessage());
    }

    // Thrown by the multipart resolver before the target handler is mapped (e.g. an artifact
    // registry upload past spring.servlet.multipart.max-file-size), so it must be handled here
    // rather than in a controller-local @ExceptionHandler.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ErrorResponse handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletResponse response) {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        return new ErrorResponse("Upload exceeds the configured maximum upload size of " + maxUploadSize +
                ". Contact your DeltaFi administrator to increase maxUploadSize.");
    }
}
