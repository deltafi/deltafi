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
package org.deltafi.core.action.metadata;

import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModifyMetadata extends TransformAction<ModifyMetadataParameters> {

    public ModifyMetadata() {
        super(ActionOptions.builder()
                .description("Adds, modifies, copies, or removes metadata.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .metadataSummary("""
                                Metadata is added, copied, or removed by an ordered list of operations, each applied
                                in turn. Operation types:
                                - ADD_OR_MODIFY: add or replace key-value pairs.
                                - COPY: copy a key's value to one or more new keys (new keys can be a comma-separated list).
                                - DELETE_KEYS: delete specific keys.
                                - DELETE_MATCHING: delete keys whose name matches a regex.
                                - REPLACE_KEY: rename keys by regex.
                                - REPLACE_VALUE: rewrite values by regex.

                                The addOrModifyMetadata, copyMetadata, and deleteMetadataKeys parameters are shortcuts
                                that run first.
                                """)
                        .build())
                .details("""
                        Below are examples of common usage patterns.

                        #### HTTP-header-safe replacement
                        Replace every character that is not a letter, digit, or dash with an underscore (e.g. `My Key!` becomes `My_Key_`):
                        ```yaml
                        name: ModifyMetadata
                        type: org.deltafi.core.action.metadata.ModifyMetadata
                        parameters:
                          operations:
                            - type: REPLACE_KEY
                              pattern: "[^A-Za-z0-9-]"
                              replacement: "_"
                              dropOriginal: true
                        ```

                        #### Conditionally add metadata
                        Apply an operation only when its `condition` template resolves to true (here, tag files from one source):
                        ```yaml
                        name: ModifyMetadata
                        type: org.deltafi.core.action.metadata.ModifyMetadata
                        parameters:
                          operations:
                            - type: ADD_OR_MODIFY
                              condition: "{{ metadata['source'] == 'sender-a' }}"
                              values:
                                priority: "high"
                                routedBy: "metadata-rule"
                        ```

                        #### Rename keys with capture groups
                        Strip a vendor prefix from key names, keeping the captured remainder via `$1` (e.g. `x-acme-customerId` becomes `customerId`):
                        ```yaml
                        name: ModifyMetadata
                        type: org.deltafi.core.action.metadata.ModifyMetadata
                        parameters:
                          operations:
                            - type: REPLACE_KEY
                              pattern: "^x-acme-(.*)$"
                              replacement: "$1"
                        ```
                        """)
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ModifyMetadataParameters params,
                                         @NotNull TransformInput input) {
        Map<String, String> original = input.getMetadata();
        Map<String, String> working = new LinkedHashMap<>(original);

        effectiveOperations(params).stream()
                .filter(MetadataOperation::isCondition)
                .forEach(operation -> operation.apply(working));

        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        // translate the working metadata back into add/delete operations relative to the input
        working.forEach((key, value) -> {
            if (!value.equals(original.get(key))) {
                result.addMetadata(key, value);
            }
        });
        original.keySet().forEach(key -> {
            if (!working.containsKey(key)) {
                result.deleteMetadataKey(key);
            }
        });

        return result;
    }

    /**
     * The convenience parameters (addOrModifyMetadata, copyMetadata, deleteMetadataKeys) are mapped to
     * operations and prepended to the operations list, so everything runs through one pipeline.
     */
    private List<MetadataOperation> effectiveOperations(ModifyMetadataParameters params) {
        List<MetadataOperation> effective = new ArrayList<>();

        if (!params.getAddOrModifyMetadata().isEmpty()) {
            MetadataOperation.AddOrModify addOrModify = new MetadataOperation.AddOrModify();
            addOrModify.setValues(params.getAddOrModifyMetadata());
            effective.add(addOrModify);
        }
        if (!params.getCopyMetadata().isEmpty()) {
            MetadataOperation.Copy copy = new MetadataOperation.Copy();
            copy.setKeysToCopy(params.getCopyMetadata());
            effective.add(copy);
        }
        if (!params.getDeleteMetadataKeys().isEmpty()) {
            MetadataOperation.DeleteKeys deleteKeys = new MetadataOperation.DeleteKeys();
            deleteKeys.setKeysToDelete(params.getDeleteMetadataKeys());
            effective.add(deleteKeys);
        }

        effective.addAll(params.getOperations());
        return effective;
    }
}
