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
package org.deltafi.actionkit.lookup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.lookup.LookupTable;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
@Slf4j
public abstract class LookupTableSupplier {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private final LookupTableClient lookupTableClient;
    @Getter
    private final LookupTable lookupTable;

    public abstract List<Map<String, String>> getRows(@NotNull Map<String, String> variables,
            @Nullable Map<String, Set<String>> matchingColumnValues, @Nullable List<String> resultColumns);

    public void uploadTable(@NotNull Map<String, String> variables) {
        try {
            uploadTable(LookupTableClient.UploadFileType.JSON, OBJECT_MAPPER.writeValueAsString(
                    getRows(variables, null, null)));
        } catch (JsonProcessingException e) {
            log.error("Unable to convert rows to JSON", e);
        }
    }

    protected void uploadTable(LookupTableClient.UploadFileType uploadFileType, String file) {
        try {
            lookupTableClient.uploadTable(lookupTable.getName(), uploadFileType, file);
        } catch (IOException e) {
            log.error("Unable to upload lookup table: {}", lookupTable.getName(), e);
        }
    }
}
