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
package org.deltafi.actionkit.action.load;

import lombok.Data;
import org.deltafi.common.types.LoadEvent;

import java.util.UUID;

@Data
public class ChildLoadResult {

    private final String did;
    private LoadResult loadResult;

    /**
     * Create a new ChildLoadResult with a populated did
     */
    public ChildLoadResult() {
        this.did = UUID.randomUUID().toString();
    }

    /**
     * Create a ChildLoadResult with the given loadResult
     * and a populated did
     * @param loadResult load result for this child
     */
    ChildLoadResult(LoadResult loadResult) {
        this.loadResult = loadResult;
        this.did = UUID.randomUUID().toString();
    }

    final LoadEvent toEvent() {
        LoadEvent loadEvent = loadResult.toEvent().getLoad();
        loadEvent.setDid(this.did);
        return loadEvent;
    }

}