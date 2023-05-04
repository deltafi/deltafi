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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for JOIN actions
 */
@Getter
@Setter
public class JoinResult extends DataAmendedResult implements JoinResultType {
    private final List<Domain> domains = new ArrayList<>();

    /**
     * @param context execution context for the current action
     */
    public JoinResult(ActionContext context) {
        super(context);
    }

    /**
     * @param context execution context for the current action
     * @param content the joined content
     */
    public JoinResult(ActionContext context, List<ActionContent> content) {
        super(context);
        setContent(content);
    }

    @SuppressWarnings("unused")
    public void addDomain(String domainName, String value, String mediaType) {
        domains.add(new Domain(domainName, value, mediaType));
    }

    @Override
    protected final ActionEventType actionEventType() {
        return ActionEventType.JOIN;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setJoin(JoinEvent.builder()
                .domains(domains)
                .content(ContentConverter.convert(content))
                .metadata(metadata)
                .build());
        return event;
    }
}
