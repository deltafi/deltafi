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
package org.deltafi.core.action.ingress;

import org.deltafi.actionkit.action.ingress.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SmokeTestIngress extends TimedIngressAction<SmokeTestParameters> {
    final Random random = new Random();

    public SmokeTestIngress() {
        super(ActionOptions.builder()
                .description("Creates smoke test DeltaFiles.")
                .build());
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull SmokeTestParameters params) {
        int index = 0;
        if (context.getMemo() != null) {
            index = 1 + Integer.parseInt(context.getMemo());
        }

        boolean sleepyTime = params.getDelayChance() > 0 && random.nextInt(params.getDelayChance()) == 0;
        if (sleepyTime) {
            try {
                Thread.sleep(Math.max(0, params.getDelayMS()));
            } catch (InterruptedException ignored) {}
        }
        boolean executeImmediate = !sleepyTime && params.getTriggerImmediateChance() > 0 && random.nextInt(params.getTriggerImmediateChance()) == 0;

        String filename = context.getFlowName() + "-" + index + (executeImmediate ? " (trigger immediate)" : "") + (sleepyTime ? " (delayed)" : "");

        IngressResultItem resultItem = new IngressResultItem(context, filename);

        if (params.getMetadata() != null && !params.getMetadata().isEmpty()) {
            resultItem.addMetadata(params.getMetadata());
        }
        resultItem.addMetadata("index", String.valueOf(index));

        byte[] content = (params.getContent() == null || params.getContent().isEmpty()) ?
                randomContent(params.getContentSize()) :
                params.getContent().getBytes();
        resultItem.saveContent(content, filename, params.getMediaType());

        IngressResult ingressResult = new IngressResult(context);
        ingressResult.addItem(resultItem);
        ingressResult.setMemo(String.valueOf(index));
        ingressResult.setExecuteImmediate(executeImmediate);
        ingressResult.setStatusMessage("Successfully created " + filename);
        return ingressResult;
    }

    private byte[] randomContent(int size) {
        byte[] contentBytes = new byte[size];
        random.nextBytes(contentBytes);
        return contentBytes;
    }
}
