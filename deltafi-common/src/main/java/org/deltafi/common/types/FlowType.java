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
package org.deltafi.common.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FlowType {
    REST_DATA_SOURCE("rest data source"),
    TIMED_DATA_SOURCE("timed data source"),
    ON_ERROR_DATA_SOURCE("on error data source"),
    TRANSFORM("transform"),
    DATA_SINK("data sink");

    private final String displayName;

    public boolean isDataSource() {
        return this == REST_DATA_SOURCE || this == TIMED_DATA_SOURCE || this == ON_ERROR_DATA_SOURCE;
    }
}
