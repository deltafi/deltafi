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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.TimedIngressAction;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.SmokeTestParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SmokeTestIngressAction extends TimedIngressAction<SmokeTestParameters> {
    public SmokeTestIngressAction() {
        super("Create smoke test DeltaFiles");
    }

    @Override
    public void ingress(@NotNull ActionContext context, @NotNull SmokeTestParameters params) {
        String did = UUID.randomUUID().toString();
        String filename = (params.getFilename() == null || params.getFilename().isEmpty()) ? did : params.getFilename();

        IngressResult result = new IngressResult(context, did, filename);

        if (params.getMetadata() != null && !params.getMetadata().isEmpty()) {
            result.addMetadata(params.getMetadata());
        }

        String content = (params.getContent() == null || params.getContent().isEmpty()) ? did : params.getContent();
        result.saveContent(content, filename, params.getMediaType());

        submitResult(result);
    }
}
