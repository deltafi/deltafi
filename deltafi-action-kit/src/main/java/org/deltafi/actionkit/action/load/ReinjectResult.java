/**
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Specialized result class for LOAD actions that reinjects one or more DeltaFiles into child flows. Each child added to
 * this result object will be ingressed as a new DeltaFile on the specified flow.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ReinjectResult extends Result<ReinjectResult> implements LoadResultType {
    final List<ReinjectEvent> reinjectEvents = new ArrayList<>();

    /**
     * @param context Execution context for the current action
     */
    public ReinjectResult(ActionContext context) {
        super(context);
    }

    /**
     * Add a new child to the result that will be ingressed as a new DeltaFile
     * @param filename Ingress file name for the new DeltaFile
     * @param flow Flow for the new DeltaFile to be ingressed on
     * @param content Content of the new DeltaFile
     * @param metadata Source metadata for the new DeltaFile
     */
    public void addChild(String filename, String flow, List<ActionContent> content, Map<String, String> metadata) {
        reinjectEvents.add(ReinjectEvent.newBuilder()
                .filename(filename)
                .flow(flow)
                .content(content.stream().map(ContentConverter::convert).toList())
                .metadata(metadata)
                .build());
    }

    @Override
    protected final ActionEventType actionEventType() {
        return ActionEventType.REINJECT;
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setReinject(reinjectEvents);
        return event;
    }
}
