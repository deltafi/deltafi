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
package org.deltafi.core.monitor.checks;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.*;

@Slf4j
public abstract class StatusCheck {
    private static final int TIMEOUT = 60;
    private static final String TIMEOUT_MSG = "Timeout occurred while running check. Check took longer than "
            + TIMEOUT + " seconds to complete.";
    private static final String ERROR_MSG = "Error occurred while running check.";

    private final ExecutorService executorService;
    @Getter @Setter
    private String description;

    StatusCheck(String description) {
        this.executorService = Executors.newSingleThreadExecutor();
        this.description = description;
    }

    public String getId() {
        return getClass().getSimpleName();
    }

    public CheckResult runCheck() {
        Future<CheckResult> futureResult = executorService.submit(this::check);
        try {
            return futureResult.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return executionError(e, ERROR_MSG, 2);
        } catch (TimeoutException e) {
            return executionError(e, TIMEOUT_MSG, 1);
        } catch (Exception e) {
            return executionError(e, ERROR_MSG, 2);
        }
    }

    private CheckResult executionError(Exception e, String message, int code) {
        StringBuilder detailedMessage = new StringBuilder(message);
        log.error(detailedMessage.toString(), e);
        detailedMessage.append("\n").append(e.getMessage()).append(ExceptionUtils.getStackTrace(e));
        return result(code, detailedMessage.toString());
    }

    /**
     * Implement the check logic here
     * @return the CheckResult
     */
    public abstract CheckResult check();

    public CheckResult result(CheckResult.ResultBuilder resultBuilder) {
        return result(resultBuilder.getCode(), resultBuilder.message());
    }

    public CheckResult result(int code, String message) {
        return new CheckResult(getId(), description, code, message);
    }
}
