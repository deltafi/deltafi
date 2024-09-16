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
package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.content.ActionContent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Join {

    /**
     * Builds an action-specific input instance used by the execute method from a list of action-specific inputs.  This
     * method is used when the action context includes a join configuration.
     * @param transformInputs the list of action-specific inputs
     * @return the combined action-specific input instance
     */
    default TransformInput join(@NotNull List<TransformInput> transformInputs) {
        List<ActionContent> allContent = new ArrayList<>();
        Map<String, String> allMetadata = new HashMap<>();
        for (TransformInput transformInput : transformInputs) {
            allContent.addAll(transformInput.getContent());
            allMetadata.putAll(transformInput.getMetadata());
        }
        return TransformInput.builder()
                .content(allContent)
                .metadata(allMetadata)
                .build();
    }
}
