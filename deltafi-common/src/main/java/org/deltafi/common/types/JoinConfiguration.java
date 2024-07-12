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
package org.deltafi.common.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record JoinConfiguration(Duration maxAge, Integer minNum, Integer maxNum, String metadataKey) {
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (minNum != null && minNum < 1) {
            errors.add("Join: minNum (" + minNum + ") is not positive");
        }
        if (maxNum != null) {
            if (maxNum < 1) {
                errors.add("Join: maxNum (" + maxNum + ") is not positive");
            } else if (minNum != null && maxNum < minNum) {
                errors.add("Join: maxNum (" + maxNum + ") is < minNum (" + minNum + ")");
            }
        }

        return errors;
    }
}

