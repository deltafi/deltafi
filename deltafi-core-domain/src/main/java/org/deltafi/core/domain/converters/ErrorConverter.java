/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ErrorDomain;

public class ErrorConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ErrorConverter() {}

    public static ErrorDomain convert(ActionEventInput event, DeltaFile deltaFile) {
        return ErrorDomain.newBuilder()
                .cause(event.getError().getCause())
                .context(event.getError().getContext())
                .fromAction(event.getAction())
                .originatorDid(event.getDid())
                .originator(deltaFile)
                .build();
    }

    public static ErrorDomain convert(Object errorDomain) {
        return objectMapper.convertValue(errorDomain, ErrorDomain.class);
    }
}