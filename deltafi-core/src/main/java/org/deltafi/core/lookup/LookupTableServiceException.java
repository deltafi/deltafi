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
package org.deltafi.core.lookup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class LookupTableServiceException extends Exception {
    private final String name;
    private final List<String> errors;

    public LookupTableServiceException(String name, String message) {
        this(name, message, null);
    }

    public LookupTableServiceException(String name, String message, List<String> errors) {
        super(message);
        this.name = name;
        this.errors = errors;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + name;
    }
}
