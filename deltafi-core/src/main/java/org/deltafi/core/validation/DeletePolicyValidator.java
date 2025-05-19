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
package org.deltafi.core.validation;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.types.DeletePolicy;
import org.deltafi.core.types.TimedDeletePolicy;

import java.time.DateTimeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DeletePolicyValidator {

    private DeletePolicyValidator() {}

    public static List<String> validate(DeletePolicy policy) {
        List<String> errors = new ArrayList<>();

        if (policy.getId() == null) {
            errors.add("id is missing");
        }

        if (StringUtils.isBlank(policy.getName())) {
            errors.add("name is missing");
        } else if (policy.getName().contains("=") || policy.getName().contains(";")) {
            errors.add("name may not contain an equals sign or semicolon");
        }

        if ((policy.getFlow() != null) && StringUtils.isBlank(policy.getFlow())) {
            errors.add("dataSource is invalid");
        }

        if (policy instanceof TimedDeletePolicy timedDeletePolicy) {
            errors.addAll(validateTimedDeletePolicy(timedDeletePolicy));
        }

        return errors;
    }

    private static List<String> validateTimedDeletePolicy(TimedDeletePolicy policy) {
        List<String> errors = new ArrayList<>();

        Duration afterCreate = parseDuration("afterCreate", policy.getAfterCreate(), errors);
        Duration afterComplete = parseDuration("afterComplete", policy.getAfterComplete(), errors);

        long minBytes = policy.getMinBytes() != null ? policy.getMinBytes() : 0;
        if (minBytes < 0) {
            errors.add("minBytes must not be negative");
        }

        if (errors.isEmpty() && ((minBytes == 0 && afterCreate == null && afterComplete == null) || (afterCreate != null && afterComplete != null))) {
            errors.add("Timed delete policy " + policy.getName() + " must specify exactly one of afterCreate or afterComplete and/or minBytes");
        }
        return errors;
    }

    private static Duration parseDuration(String label, String text, List<String> errors) {
        if (text != null) {
            try {
                return Duration.parse(text);
            } catch (DateTimeException e) {
                errors.add("Unable to parse duration for " + label);
            }
        }
        return null;
    }

}