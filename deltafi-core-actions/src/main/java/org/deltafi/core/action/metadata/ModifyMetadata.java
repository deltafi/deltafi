/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.metadata;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ModifyMetadata extends TransformAction<ModifyMetadataParameters> {

    public ModifyMetadata() {
        super("Adds, modifies, copies, or removes metadata.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ModifyMetadataParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        result.setMetadata(params.getAddOrModifyMetadata());

        params.getCopyMetadata().forEach((key, value) -> {
            if (input.getMetadata().containsKey(key)) {
                String[] newKeys = value.split(",");

                for (String newKey : newKeys) {
                    String trimmedKey = newKey.trim();
                    result.addMetadata(trimmedKey, input.getMetadata().get(key));
                }
            }
        });

        params.getDeleteMetadataKeys().forEach(key -> {
            // if this was just added, de-add it
            result.getMetadata().remove(key);

            // if it was in the input, remove it
            if (input.getMetadata().containsKey(key)) {
                result.deleteMetadataKey(key);
            }
        });

        return result;
    }
}
