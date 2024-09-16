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
package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static graphql.Assert.assertFalse;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeleteContentTest {
    private static final String NAME0 = "file0.jpg"; // image/jpeg
    private static final String NAME1 = "file1.mp4"; // video/mp4
    private static final String NAME2 = "file2.png"; // image/png

    DeleteContent action = new DeleteContent();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();
    ActionContext context = runner.actionContext();

    @Test
    void testDeleteAll() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setDeleteAllContent(true);

        runTest(params, 0);
    }

    @Test
    void testAllowedIndexesNoneMatch() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setAllowedIndexes(List.of(3)); // indexes start at 0

        runTest(params, 0);
    }

    @Test
    void testAllowedIndexes() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setAllowedIndexes(List.of(0, 2));

        TransformResult result = runTest(params, 2);
        assertEquals(NAME0, result.getContent().get(0).getName());
        assertEquals(NAME2, result.getContent().get(1).getName());
    }

    @Test
    void testProhibitedIndexesNoneMatch() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setProhibitedIndexes(List.of(3));

        TransformResult result = runTest(params, 3);
        assertEquals(NAME0, result.getContent().get(0).getName());
        assertEquals(NAME1, result.getContent().get(1).getName());
        assertEquals(NAME2, result.getContent().get(2).getName());
    }

    @Test
    void testProhibitedIndexes() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setProhibitedIndexes(List.of(0, 2));

        TransformResult result = runTest(params, 1);
        assertEquals(NAME1, result.getContent().get(0).getName());
    }

    @Test
    void testAllowedFilePatterns() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setAllowedFilePatterns(List.of("*.mp*"));

        TransformResult result = runTest(params, 1);
        assertEquals(NAME1, result.getContent().get(0).getName());
    }

    @Test
    void testProhibitedFilePatterns() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setProhibitedFilePatterns(List.of("*.mp*"));

        TransformResult result = runTest(params, 2);
        assertEquals(NAME0, result.getContent().get(0).getName());
        assertEquals(NAME2, result.getContent().get(1).getName());
    }

    @Test
    void testAllowedMediaTypes() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setAllowedMediaTypes(List.of("image/*"));

        TransformResult result = runTest(params, 2);
        assertEquals(NAME0, result.getContent().get(0).getName());
        assertEquals(NAME2, result.getContent().get(1).getName());
    }

    @Test
    void testProhibitedMediaTypes() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setProhibitedMediaTypes(List.of("image/*"));

        TransformResult result = runTest(params, 1);
        assertEquals(NAME1, result.getContent().get(0).getName());
    }

    @Test
    void testCombinations1() {
        DeleteContentParameters params = new DeleteContentParameters();
        // No affect:
        params.setDeleteAllContent(false);
        // Gets rid of NAME0
        params.setAllowedIndexes(List.of(1, 2));
        // Ignored b/c there was an allowedIndex list:
        params.setProhibitedIndexes(List.of(0, 1, 2));
        // Matches NAME1 and NAME2:
        params.setAllowedFilePatterns(List.of("file*"));
        // Removes NAME2
        params.setProhibitedMediaTypes(List.of("image/png"));

        TransformResult result = runTest(params, 1);
        assertEquals(NAME1, result.getContent().get(0).getName());
    }

    @Test
    void testCombinations2() {
        DeleteContentParameters params = new DeleteContentParameters();
        // Removes NAME1:
        params.setProhibitedIndexes(List.of(1, 99));
        // Removes NAME0:
        params.setProhibitedFilePatterns(List.of("file0*", "blah"));
        // No affect:
        params.setProhibitedMediaTypes(List.of("text/plain"));

        TransformResult result = runTest(params, 1);
        assertEquals(NAME2, result.getContent().get(0).getName());
    }

    @Test
    void testMatchesPatternNoWildcards() {
        assertTrue(action.matchesPattern("test", "test"));
        assertFalse(action.matchesPattern("test", "tes"));
        assertFalse(action.matchesPattern("file2.mp4", ".mp"));
    }

    @Test
    void testMatchesPatternWithWildcards() {
        assertTrue(action.matchesPattern("test", "test*"));
        assertTrue(action.matchesPattern("test.txt", "test*"));
        assertFalse(action.matchesPattern("something.test", "test*"));
        assertFalse(action.matchesPattern("file2.mp4", ".mp*"));
        assertTrue(action.matchesPattern("file2.mp4", "*.mp*"));
    }

    private TransformResult runTest(DeleteContentParameters params, int count) {
        TransformInput input = threeInputs();
        ResultType result = action.transform(context, params, input);
        assertTransformResult(result).hasContentCount(count);
        TransformResult transformResult = (TransformResult) result;
        return transformResult;
    }

    private TransformInput threeInputs() {
        return TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent(NAME0, "image/jpeg"),
                        runner.saveEmptyContent(NAME1, "video/mp4"),
                        runner.saveEmptyContent(NAME2, "image/png")))
                .build();
    }


}
