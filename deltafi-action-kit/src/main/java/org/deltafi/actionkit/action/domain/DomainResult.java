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
package org.deltafi.actionkit.action.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.AnnotationsResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.DomainEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized result class for DOMAIN actions
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class DomainResult extends AnnotationsResult<DomainResult> implements DomainResultType {
    /**
     * @param context Context of the executed action
     */
    public DomainResult(@NotNull ActionContext context) {
        super(context, ActionEventType.DOMAIN);
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setDomain(DomainEvent.builder().annotations(annotations).build());
        return event;
    }
}
