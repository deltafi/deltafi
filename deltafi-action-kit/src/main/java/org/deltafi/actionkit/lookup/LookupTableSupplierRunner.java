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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.common.lookup.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class LookupTableSupplierRunner {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private final ActionEventQueue actionEventQueue;
    @Getter
    private final Map<String, LookupTableSupplier> lookupTableSupplierMap;

    public LookupTableSupplierRunner(ActionEventQueue actionEventQueue,
            List<LookupTableSupplier> lookupTableSuppliers) {
        this.actionEventQueue = actionEventQueue;

        lookupTableSupplierMap = lookupTableSuppliers.stream().collect(Collectors.toMap(
                lookupTableSupplier -> lookupTableSupplier.getLookupTable().getName(), Function.identity()));

        if (!lookupTableSupplierMap.isEmpty()) {
            EXECUTOR_SERVICE.submit(this::listen);
        }
    }

    private void listen() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                LookupTableEvent lookupTableEvent = actionEventQueue.takeLookupTableEvent(
                        lookupTableSupplierMap.keySet().toArray(String[]::new));

                LookupTableSupplier lookupTableSupplier =
                        lookupTableSupplierMap.get(lookupTableEvent.getLookupTableName());

                actionEventQueue.putLookupTableResult(new LookupTableEventResult(lookupTableEvent.getId(),
                        lookupTableEvent.getLookupTableName(), lookupTableSupplier.getRows(
                        lookupTableEvent.getVariables(), lookupTableEvent.getMatchingColumnValues(), lookupTableEvent.getResultColumns()
                )));
            } catch (Exception e) {
                log.error("Unexpected exception", e);
                EXECUTOR_SERVICE.submit(this::listen);
            }
        }
    }
}
