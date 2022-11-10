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
package org.deltafi.actionkit.action.transform;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ProtocolLayer;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.TransformEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized result class for TRANSFORM actions
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class TransformResult extends DataAmendedResult implements TransformResultType{
    /**
     * @param context Context of executing action
     */
    public TransformResult(@NotNull ActionContext context) {
        super(context);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.TRANSFORM;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setTransform(TransformEvent.newBuilder()
                .protocolLayer(new ProtocolLayer(context.getName(), content, metadata))
                .build());
        return event;
    }
}
