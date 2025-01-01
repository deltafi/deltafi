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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentSelectionParametersTest {
    private static final List<ActionContent> CONTENT = List.of(
            ActionContent.emptyContent(ActionContext.builder().build(), "file0.jpg", "image/jpeg"),
            ActionContent.emptyContent(ActionContext.builder().build(), "file1.mp4", "video/mp4"),
            ActionContent.emptyContent(ActionContext.builder().build(), "file2.png", "image/png")
    );

    @Test
    public void contentSelectedByIndex() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setContentIndexes(List.of(0, 2));

        assertTrue(params.contentSelected(0, CONTENT.get(0)));
        assertFalse(params.contentSelected(1, CONTENT.get(1)));
        assertTrue(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void contentSelectedByExcludedIndex() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setContentIndexes(List.of(0, 2));
        params.setExcludeContentIndexes(true);

        assertFalse(params.contentSelected(0, CONTENT.get(0)));
        assertTrue(params.contentSelected(1, CONTENT.get(1)));
        assertFalse(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void contentSelectedByFilePattern() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setFilePatterns(List.of("*.jpg", "*.png"));

        assertTrue(params.contentSelected(0, CONTENT.get(0)));
        assertFalse(params.contentSelected(1, CONTENT.get(1)));
        assertTrue(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void contentSelectedByExcludedFilePattern() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setFilePatterns(List.of("*.jpg", "*.png"));
        params.setExcludeFilePatterns(true);

        assertFalse(params.contentSelected(0, CONTENT.get(0)));
        assertTrue(params.contentSelected(1, CONTENT.get(1)));
        assertFalse(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void contentSelectedByMediaType() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setMediaTypes(List.of("image/*"));

        assertTrue(params.contentSelected(0, CONTENT.get(0)));
        assertFalse(params.contentSelected(1, CONTENT.get(1)));
        assertTrue(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void contentSelectedByExcludedMediaType() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        params.setMediaTypes(List.of("image/*"));
        params.setExcludeMediaTypes(true);

        assertFalse(params.contentSelected(0, CONTENT.get(0)));
        assertTrue(params.contentSelected(1, CONTENT.get(1)));
        assertFalse(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void testCombinations1() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        // Has an index of 1, 2, or 99 (CONTENT[1] and CONTENT[2]):
        params.setContentIndexes(List.of(1, 2, 99));
        // Has a name that begins with "file" (CONTENT[1] and CONTENT[2]):
        params.setFilePatterns(List.of("file*"));
        // Does not have a media type of "image/png" (CONTENT[1]):
        params.setMediaTypes(List.of("image/png"));
        params.setExcludeMediaTypes(true);

        assertFalse(params.contentSelected(0, CONTENT.get(0)));
        assertTrue(params.contentSelected(1, CONTENT.get(1)));
        assertFalse(params.contentSelected(2, CONTENT.get(2)));
    }

    @Test
    public void testCombinations2() {
        ContentSelectionParameters params = new ContentSelectionParameters();
        // Does not have an index of 0, or 99 (CONTENT[1] and CONTENT[2]):
        params.setContentIndexes(List.of(0, 99));
        params.setExcludeContentIndexes(true);
        // Does not have a name that begins with "file2*" (CONTENT[1]):
        params.setFilePatterns(List.of("file2*", "blah"));
        params.setExcludeFilePatterns(true);
        // Has a media type of "video/mp4" (CONTENT[1]):
        params.setMediaTypes(List.of("video/mp4"));

        assertFalse(params.contentSelected(0, CONTENT.get(0)));
        assertTrue(params.contentSelected(1, CONTENT.get(1)));
        assertFalse(params.contentSelected(2, CONTENT.get(2)));
    }
}
