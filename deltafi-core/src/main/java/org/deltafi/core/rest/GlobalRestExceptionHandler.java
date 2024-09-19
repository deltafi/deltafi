/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

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
}
