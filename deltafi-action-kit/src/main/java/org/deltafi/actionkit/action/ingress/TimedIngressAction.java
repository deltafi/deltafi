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
package org.deltafi.actionkit.action.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.exception.IngressException;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.UUID;

/**
 * Specialization class for TIMED INGRESS actions.
 * @param <P> Parameter class for configuring the Timed Ingress action
 */
public abstract class TimedIngressAction<P extends ActionParameters> extends Action<P> {
    @Lazy
    @Autowired
    private ActionEventQueue actionEventQueue;

    public TimedIngressAction(String description) {
        super(ActionType.TIMED_INGRESS, description);
    }

    @Override
    protected final IngressResultType execute(@NotNull List<DeltaFileMessage> deltaFileMessages,
                                              @NotNull ActionContext context,
                                              @NotNull P params) {
        ingress(context, params);
        return null;
    }

    /**
     * Implements the ingress execution function of a timed ingress action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @see IngressResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see org.deltafi.actionkit.action.ReinjectResult
     */
    public abstract void ingress(@NotNull ActionContext context, @NotNull P params);

    /**
     * Ingress a new DeltaFile by placing it on the result queue
     * @param result The result of the timed ingress action
     * @throws IngressException Thrown if an error occurs enqueueing the result
     */
    public void submitResult(IngressResultType result) throws IngressException {
        try {
            actionEventQueue.putResult(result.toEvent(), null);
        } catch (JsonProcessingException e) {
            throw new IngressException("Failed to enqueue ingress result", e);
        }
    }

    /**
     * Ingress a new DeltaFile by placing it on the result queue
     * @param results The result of the timed ingress action
     * @throws IngressException Thrown if an error occurs enqueueing the result
     */
    public void submitResults(List<IngressResultType> results) throws IngressException {
        try {
            actionEventQueue.putResults(results.stream().map(IngressResultType::toEvent).toList(), null);
        } catch (JsonProcessingException e) {
            throw new IngressException("Failed to enqueue ingress results", e);
        }
    }
}
