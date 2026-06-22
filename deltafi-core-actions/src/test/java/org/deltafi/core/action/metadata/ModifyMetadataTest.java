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

import tools.jackson.databind.ObjectMapper;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ModifyMetadataTest {

    ModifyMetadata action = new ModifyMetadata();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testTransformWithConvenienceParameters() {
        ModifyMetadataParameters params = new ModifyMetadataParameters();
        params.setAddOrModifyMetadata(new HashMap<>(Map.of("key1", "value1", "key2", "value2")));
        params.setCopyMetadata(Map.of("origKey1", "key3, key4 ,key5"));
        params.setDeleteMetadataKeys(List.of("origKey2", "origKey3", "key5"));

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("origKey1", "origVal1", "origKey2", "origVal2")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .addedMetadata(Map.of("key1", "value1", "key2", "value2", "key3", "origVal1", "key4", "origVal1"))
                .deletedMetadataKey("origKey2");
    }

    @Test
    void testAddOrModifyOperation() {
        ModifyMetadataParameters params = paramsWith(addOrModify(Map.of("stage", "ingested")));

        TransformInput input = TransformInput.builder().metadata(Map.of("existing", "value")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result).addedMetadata("stage", "ingested");
    }

    @Test
    void testDeleteMatchingOperation() {
        MetadataOperation.DeleteMatching deleteMatching = new MetadataOperation.DeleteMatching();
        deleteMatching.setPattern("^tmp_.*");
        ModifyMetadataParameters params = paramsWith(deleteMatching);

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("tmp_a", "1", "tmp_b", "2", "keep", "3")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .deletedMetadataKey("tmp_a")
                .deletedMetadataKey("tmp_b");
    }

    @Test
    void testReplaceKeyRenamesInputKey() {
        ModifyMetadataParameters params = paramsWith(replaceKey("\\s+", "_", true));

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("Content Type", "application/json", "noSpaces", "leaveMe", "Data Source", "std in")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .addedMetadata("Content_Type", "application/json")
                .addedMetadata("Data_Source", "std in")
                .deletedMetadataKey("Content Type")
                .deletedMetadataKey("Data Source");
    }

    @Test
    void testReplaceKeyCopyWhenDropOriginalFalse() {
        ModifyMetadataParameters params = paramsWith(replaceKey("\\s+", "_", false));

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("Content Type", "application/json")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .addedMetadata("Content_Type", "application/json")
                .deletedMetadataKeys(List.of());
    }

    @Test
    void testReplaceValueScopedByKeyPattern() {
        MetadataOperation.ReplaceValue replaceValue = new MetadataOperation.ReplaceValue();
        replaceValue.setKeyPattern("^path.*");
        replaceValue.setPattern("\\\\");
        replaceValue.setReplacement("/");
        ModifyMetadataParameters params = paramsWith(replaceValue);

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("path1", "a\\b\\c", "other", "x\\y")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        // only the key matching the keyPattern has its value rewritten
        TransformResultAssert.assertThat(result).addedMetadata("path1", "a/b/c");
    }

    @Test
    void testConditionFalseSkipsOperation() {
        MetadataOperation.AddOrModify addOrModify = addOrModify(Map.of("stage", "ingested"));
        addOrModify.setCondition(false);
        ModifyMetadataParameters params = paramsWith(addOrModify);

        TransformInput input = TransformInput.builder().metadata(Map.of("existing", "value")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        // nothing added or deleted because the condition resolved to false
        TransformResultAssert.assertThat(result).metadataIsEmpty();
    }

    @Test
    void testOperationsAppliedInOrderOnRunningState() {
        // rename "raw key" -> "raw_key", then copy "raw_key" -> "final"
        MetadataOperation.Copy copy = new MetadataOperation.Copy();
        copy.setKeysToCopy(Map.of("raw_key", "final"));
        ModifyMetadataParameters params = paramsWith(replaceKey("\\s+", "_", true), copy);

        TransformInput input = TransformInput.builder().metadata(Map.of("raw key", "v")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .addedMetadata("raw_key", "v")
                .addedMetadata("final", "v")
                .deletedMetadataKey("raw key");
    }

    @Test
    void testPolymorphicDeserialization() throws Exception {
        String json = """
                {
                  "operations": [
                    { "type": "DELETE_MATCHING", "pattern": "^tmp_.*" },
                    { "type": "REPLACE_KEY", "pattern": "\\\\s+", "replacement": "_", "condition": false },
                    { "type": "ADD_OR_MODIFY", "values": { "stage": "ingested" } }
                  ]
                }
                """;

        ModifyMetadataParameters params = new ObjectMapper().readValue(json, ModifyMetadataParameters.class);

        assertThat(params.getOperations()).hasSize(3);
        assertThat(params.getOperations().get(0)).isInstanceOf(MetadataOperation.DeleteMatching.class);
        assertThat(params.getOperations().get(1)).isInstanceOf(MetadataOperation.ReplaceKey.class);
        assertThat(params.getOperations().get(1).isCondition()).isFalse();
        assertThat(params.getOperations().get(2)).isInstanceOf(MetadataOperation.AddOrModify.class);
    }

    private ModifyMetadataParameters paramsWith(MetadataOperation... operations) {
        ModifyMetadataParameters params = new ModifyMetadataParameters();
        params.setOperations(new ArrayList<>(Arrays.asList(operations)));
        return params;
    }

    private MetadataOperation.AddOrModify addOrModify(Map<String, String> values) {
        MetadataOperation.AddOrModify addOrModify = new MetadataOperation.AddOrModify();
        addOrModify.setValues(new HashMap<>(values));
        return addOrModify;
    }

    private MetadataOperation.ReplaceKey replaceKey(String pattern, String replacement, boolean dropOriginal) {
        MetadataOperation.ReplaceKey replaceKey = new MetadataOperation.ReplaceKey();
        replaceKey.setPattern(pattern);
        replaceKey.setReplacement(replacement);
        replaceKey.setDropOriginal(dropOriginal);
        return replaceKey;
    }
}
