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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

@Slf4j
@SuppressWarnings("unused")
public abstract class ResourceLookupTableSupplier extends LookupTableSupplier {
    private final String path;

    public ResourceLookupTableSupplier(LookupTableClient lookupTableClient, LookupTable lookupTable, String path) {
        super(lookupTableClient, lookupTable);
        this.path = path;
    }

    @Override
    public List<Map<String, String>> getRows(@NotNull Map<String, String> variables,
            @Nullable Map<String, Set<String>> matchingColumnValues, @Nullable List<String> resultColumns) {
        uploadTable(variables);
        return Collections.emptyList();
    }

    @Override
    public void uploadTable(@NotNull Map<String, String> variables) {
        try {
            uploadTable(path.endsWith(".csv") ? LookupTableClient.UploadFileType.CSV :
                    LookupTableClient.UploadFileType.JSON, Resource.read(path));
        } catch (IOException e) {
            log.error("Unable to read resource: {}", path, e);
        }
    }
}
