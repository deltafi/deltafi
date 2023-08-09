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

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.ModifyMetadataParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ModifyMetadataTransformAction extends TransformAction<ModifyMetadataParameters> {

    public ModifyMetadataTransformAction() {
        super("Add, modify, copy, and remove metadata");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ModifyMetadataParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        if (params.getAddOrModifyMetadata() != null) {
            result.setMetadata(params.getAddOrModifyMetadata());
        }

        if (params.getCopyMetadata() != null) {
            params.getCopyMetadata().forEach((key, value) -> {
                if (input.getMetadata().containsKey(key)) {
                    String[] newKeys = value.split(",");

                    for (String newKey : newKeys) {
                        String trimmedKey = newKey.trim();
                        result.addMetadata(trimmedKey, input.getMetadata().get(key));
                    }
                }
            });
        }

        if (params.getDeleteMetadataKeys() != null) {
            params.getDeleteMetadataKeys().forEach(key -> {
                // if this was just added, de-add it
                result.getMetadata().remove(key);

                // if it was in the input, remove it
                if (input.getMetadata().containsKey(key)) {
                    result.deleteMetadataKey(key);
                }
            });
        }

        return result;
    }
}
