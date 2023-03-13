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
package org.deltafi.core.types;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ResumePolicy extends org.deltafi.core.generated.types.ResumePolicy {
    public static final String INVALID_DELAY = "delay must not be negative";
    public static final String INVALID_MAX_ATTEMPTS = "maxAttempts must be positive";
    public static final String INVALID_MAX_DELAY = "maxDelay must not be negative";
    public static final String INVALID_MULTIPLIER = "multiplier must be positive";
    public static final String MAX_DELAY_ERROR = "maxDelay must not be lower than delay";
    public static final String MISSING_CRITERIA =
            "Must specify errorSubstring, flow, action, and/or actionType";
    public static final String MISSING_ID = "missing id";
    public static final String MISSING_BACKOFF = "missing backOff";
    public static final String MISSING_MAX_DELAY = "Must set maxDelay when random is true";

    /**
     * Validate the resume policy for any errors.
     *
     * @return a list of errors, or an empty list if none found
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isBlank(getId())) {
            errors.add(MISSING_ID);
        }

        if (StringUtils.isBlank(getErrorSubstring()) &&
                StringUtils.isBlank(getFlow()) &&
                StringUtils.isBlank(getAction()) &&
                StringUtils.isBlank(getActionType())) {
            errors.add(MISSING_CRITERIA);
        }

        if (getMaxAttempts() < 1) {
            errors.add(INVALID_MAX_ATTEMPTS);
        }

        if (getBackOff() == null) {
            errors.add(MISSING_BACKOFF);
        } else {
            validateBackoff(errors);
        }

        return errors;
    }

    private void validateBackoff(List<String> errors) {
        if (getBackOff().getDelay() < 0) {
            errors.add(INVALID_DELAY);
        }
        boolean random = (getBackOff().getRandom() != null) && getBackOff().getRandom();
        boolean hasMaxDelay = getBackOff().getMaxDelay() != null;
        boolean hasMultiplier = getBackOff().getMultiplier() != null;

        if (random && !hasMaxDelay) {
            errors.add(MISSING_MAX_DELAY);
        }

        if (hasMaxDelay) {
            if (getBackOff().getMaxDelay()
                    < getBackOff().getDelay()) {
                errors.add(MAX_DELAY_ERROR);
            }
            if (getBackOff().getMaxDelay() < 0) {
                errors.add(INVALID_MAX_DELAY);
            }
        }

        if (hasMultiplier && getBackOff().getMultiplier() < 1) {
            errors.add(INVALID_MULTIPLIER);
        }
    }

    public boolean isMatch(String error, String flow, String action, String actionType) {
        return errorMatch(error) &&
                canMatch(getFlow(), flow) &&
                canMatch(getAction(), action) &&
                canMatch(getActionType(), actionType);
    }

    private boolean errorMatch(String error) {
        return StringUtils.isBlank(getErrorSubstring()) ||
                error.contains(getErrorSubstring());
    }

    private boolean canMatch(String objectValue, String searchValue) {
        return StringUtils.isBlank(objectValue) || objectValue.equals(searchValue);
    }
}
