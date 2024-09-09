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
package org.deltafi.core.types;

import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.generated.types.BackOff;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "resume_policies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"errorSubstring", "action", "actionType"})
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @lombok.Builder.Default
    private UUID id = Generators.timeBasedEpochGenerator().generate();
    @Column(nullable = false, unique = true)
    private String name;
    private String errorSubstring;
    private String dataSource;
    private String action;
    private ActionType actionType;
    private int maxAttempts;
    private Integer priority;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private BackOff backOff;

    public static final String INVALID_DELAY = "delay must not be negative";
    public static final String INVALID_MAX_ATTEMPTS = "maxAttempts must be greater than 1";
    public static final String INVALID_MAX_DELAY = "maxDelay must not be negative";
    public static final String INVALID_MULTIPLIER = "multiplier must be positive";
    public static final String MAX_DELAY_ERROR = "maxDelay must not be lower than delay";
    public static final String MISSING_CRITERIA = "Must specify errorSubstring, flow, action, and actionType";
    public static final String MISSING_ID = "missing id";
    public static final String MISSING_NAME = "missing name";
    public static final String MISSING_BACKOFF = "missing backOff";
    public static final String MISSING_MAX_DELAY = "Must set maxDelay when random is true";

    /**
     * Validate the resume policy for any errors.
     *
     * @return a list of errors, or an empty list if none found
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (getId() == null) {
            errors.add(MISSING_ID);
        }

        if (StringUtils.isBlank(getName())) {
            errors.add(MISSING_NAME);
        }

        if (getPriority() == null) {
            setPriority(computePriority());
        }

        if (StringUtils.isBlank(getErrorSubstring()) &&
                StringUtils.isBlank(getDataSource()) &&
                StringUtils.isBlank(getAction()) &&
                getActionType() == null) {
            errors.add(MISSING_CRITERIA);
        }

        if (getMaxAttempts() < 2) {
            errors.add(INVALID_MAX_ATTEMPTS);
        }

        if (getBackOff() == null) {
            errors.add(MISSING_BACKOFF);
        } else {
            validateBackoff(errors);
        }

        return errors;
    }

    private int computePriority() {
        int tempPriority = 0;
        if (StringUtils.isNotBlank(getErrorSubstring())) {
            if (getErrorSubstring().length() > 10) {
                tempPriority += 100;
            } else {
                tempPriority += 50;
            }
        }
        if (StringUtils.isNotBlank(getAction())) {
            tempPriority += 100;
        } else {
            if (getActionType() != null) {
                tempPriority += 50;
            }
        }

        if (StringUtils.isNotBlank(getDataSource())) {
            tempPriority += 50;
        }
        return tempPriority;
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

    public boolean isMatch(Action action, String dataSource) {
        return isMatch(action.getAttempt(), action.getErrorCause(), dataSource,
                action.getName(), action.getType());
    }

    /**
     * Compare contents of this policy with provided parameters
     * values (pulled from the DeltaFile/Action where the error
     * occurred) to see if they are satisfied.
     *
     * @param attempt    the number of times the action was attempted
     * @param error      the errorCause message from the action
     * @param flow       the DeltaFile;s sourceInfo flow
     * @param action     the name of the action, including flow prefix
     * @param actionType the action type
     * @return true if a match; otherwise false
     */
    public boolean isMatch(int attempt, String error, String flow, String action, ActionType actionType) {
        return attempt < getMaxAttempts() &&
                errorMatch(error) &&
                canMatch(getDataSource(), flow) &&
                canMatch(getAction(), action) &&
                actionTypeMatches(actionType);
    }

    private boolean errorMatch(String error) {
        return StringUtils.isBlank(getErrorSubstring()) ||
                error.contains(getErrorSubstring());
    }

    private boolean canMatch(String objectValue, String searchValue) {
        return StringUtils.isBlank(objectValue) || objectValue.equals(searchValue);
    }

    private boolean actionTypeMatches(ActionType actionType) {
        return this.getActionType() == null || this.getActionType() == actionType;
    }
}
