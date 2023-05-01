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
package org.deltafi.actionkit.action.join;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.common.types.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for JOIN actions
 */
@Getter
@Setter
public class JoinReinjectResult extends DataAmendedResult implements JoinResultType {
    private String flow = null;

    /**
     * @param context execution context for the current action
     * @param flow the flow to send the joined content to
     */
    public JoinReinjectResult(ActionContext context, String flow) {
        super(context);
        this.flow = flow;
    }

    /**
     * @param context execution context for the current action
     * @param flow the flow to send the joined content to
     * @param content the joined content
     */
    public JoinReinjectResult(ActionContext context, String flow, List<Content> content) {
        super(context);
        this.flow = flow;
        setContent(content);
    }

    @Override
    protected final ActionEventType actionEventType() {
        return ActionEventType.JOIN_REINJECT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setJoinReinject(JoinReinjectEvent.builder()
                .flow(flow)
                .content(content)
                .metadata(metadata)
                .build());
        return event;
    }
}
