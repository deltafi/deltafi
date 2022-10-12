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
package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.*;
import org.deltafi.common.converters.KeyValueConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Specialized result class for LOAD actions that split DeltaFiles into multiple child flows.  Each child added to
 * this result object will be ingressed as a new DeltaFile on the specified flow.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class SplitResult extends Result implements LoadResultType {
    List<SplitInput> splitInputs = new ArrayList<>();

    /**
     * @param context Execution context for the current action
     */
    public SplitResult(ActionContext context) {
        super(context);
    }

    /**
     * Add a new child to the result that will be ingressed as a new DeltaFile
     * @param filename Ingress file name for the new DeltaFile
     * @param flow Flow for the new DeltaFile to be ingressed on
     * @param metadata Source metadata for the new DeltaFile
     * @param content Content of the new DeltaFile
     */
    public void addChild(String filename, String flow, List<KeyValue> metadata, List<Content> content) {
        splitInputs.add(SplitInput.newBuilder()
                .sourceInfo(new SourceInfo(filename, flow, metadata))
                .content(content)
                .build());
    }

    /**
     * Add a new child to the result that will be ingressed as a new DeltaFile
     * @param filename Ingress file name for the new DeltaFile
     * @param flow Flow for the new DeltaFile to be ingressed on
     * @param metadata Source metadata for the new DeltaFile
     * @param content Content of the new DeltaFile
     */
    @SuppressWarnings("unused")
    public void addChild(String filename, String flow, Map<String, String> metadata, List<Content> content) {
        addChild(filename, flow, KeyValueConverter.fromMap(metadata), content);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.SPLIT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setSplit(splitInputs);
        return event;
    }
}
