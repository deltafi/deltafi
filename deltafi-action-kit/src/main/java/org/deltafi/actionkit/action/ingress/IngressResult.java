/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Result type returned by timed ingress actions containing ingressed items.
// ABOUTME: Produces INGRESS action events with memo, status, and child ingress items.
package org.deltafi.actionkit.action.ingress;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for INGRESS actions
 */
@Getter
@Setter
public class IngressResult extends Result<IngressResult> implements IngressResultType {
    String memo;
    List<IngressResultItem> ingressResultItems = new ArrayList<>();
    boolean executeImmediate = false;
    IngressStatus status = IngressStatus.HEALTHY;
    String statusMessage;

    /**
     * Create an IngressResult with the given context.
     * @param context execution context of the ingress action
     */
    public IngressResult(ActionContext context) {
        super(context, ActionEventType.INGRESS);
    }

    /**
     * Add an ingress result item to this result.
     * @param item the ingress result item to add
     */
    public void addItem(IngressResultItem item) {
        ingressResultItems.add(item);
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setIngress(IngressEvent.builder()
                        .memo(memo)
                        .executeImmediate(executeImmediate)
                        .ingressItems(ingressResultItems.stream().map(IngressResultItem::toIngressEventItem).toList())
                        .status(status)
                        .statusMessage(statusMessage)
                .build());
        return event;
    }
}
