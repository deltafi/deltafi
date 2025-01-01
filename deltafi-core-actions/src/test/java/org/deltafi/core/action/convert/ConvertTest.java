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
package org.deltafi.core.action.convert;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

class ConvertTest {

    Convert action = new Convert();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testConvertJsonToXml() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "{\"name\":\"John\",\"place\": \"Ohio\"}",
                "<xml><name>John</name><place>Ohio</place></xml>",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertJsonArrayToXml() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "[{\"name\":\"John\"},{\"name\":\"Jane\", \"favoriteThings\": [\"music\", \"movies\"]}]",
                "<xml><listEntry><name>John</name></listEntry><listEntry><name>Jane</name><favoriteThings>music</favoriteThings><favoriteThings>movies</favoriteThings></listEntry></xml>",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertXmlToJson() {
        convertAndAssert(
                DataFormat.XML,
                DataFormat.JSON,
                "example.json",
                "<xml><name>John</name></xml>",
                "{\"name\":\"John\"}",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertXmlListToJson() {
        convertAndAssert(
                DataFormat.XML,
                DataFormat.JSON,
                "example.json",
                "<xml><listEntry><name>John</name><place>Ohio</place></listEntry><listEntry><name>Jane</name><place>New York</place></listEntry></xml>",
                "{\"listEntry\":[{\"name\":\"John\",\"place\":\"Ohio\"},{\"name\":\"Jane\",\"place\":\"New York\"}]}",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertCsvToJson() {
        convertAndAssert(
                DataFormat.CSV,
                DataFormat.JSON,
                "example.json",
                "name,place\nJohn,Ohio\nJane,New York",
                "[{\"name\":\"John\",\"place\":\"Ohio\"},{\"name\":\"Jane\",\"place\":\"New York\"}]",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertCsvToXml() {
        convertAndAssert(
                DataFormat.CSV,
                DataFormat.XML,
                "example.xml",
                "name,place\nJohn,Ohio\nJane,New York",
                "<xml><listEntry><name>John</name><place>Ohio</place></listEntry><listEntry><name>Jane</name><place>New York</place></listEntry></xml>",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertJsonToCsv() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.CSV,
                "example.csv",
                "[{\"name\":\"John\",\"place\":\"Ohio\"},{\"name\":\"Jane\",\"place\":\"New York\"}]",
                "name,place\nJohn,Ohio\nJane,\"New York\"\n",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertXmlToCsv() {
        convertAndAssert(
                DataFormat.XML,
                DataFormat.CSV,
                "example.csv",
                "<xml><listEntry><name>John</name><place>Ohio</place></listEntry><listEntry><name>Jane</name><place>New York</place></listEntry></xml>",
                "name,place\nJohn,Ohio\nJane,\"New York\"\n",
                null,
                null,
                null,
                false
        );
    }

    @Test
    void testConvertingWithFilePatterns() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "{\"name\":\"John\"}",
                "<xml><name>John</name></xml>",
                null,
                List.of("*.json"),
                null,
                false
        );
    }

    @Test
    void testConvertingWithMediaTypes() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "{\"name\":\"John\"}",
                "<xml><name>John</name></xml>",
                List.of("application/json"),
                null,
                null,
                false
        );
    }

    @Test
    void testConvertingWithContentIndexes() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "{\"name\":\"John\"}",
                "<xml><name>John</name></xml>",
                null,
                null,
                List.of(0),
                false
        );
    }

    @Test
    void testNoConvertingBecauseNoMatchingCriteria() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.json",
                DataFormat.JSON.getMediaType(),
                "{\"name\":\"John\"}",
                "{\"name\":\"John\"}",
                List.of("a/b"),
                List.of("xyz"),
                List.of(1),
                false
        );
    }

    @Test
    void testConvertingAndRetainExistingContent() {
        convertAndAssert(
                DataFormat.JSON,
                DataFormat.XML,
                "example.xml",
                "{\"name\":\"John\"}",
                "<xml><name>John</name></xml>",
                null,
                null,
                null,
                true
        );
    }

    private void convertAndAssert(DataFormat inputFormat, DataFormat outputFormat, String outputContentName,
            String inputContent, String expectedOutputContent, List<String> mediaTypes, List<String> filePatterns,
            List<Integer> contentIndexes, boolean retainExistingContent) {
        convertAndAssert(inputFormat, outputFormat, outputContentName, outputFormat.getMediaType(), inputContent,
                expectedOutputContent, mediaTypes, filePatterns, contentIndexes, retainExistingContent);
    }

    private void convertAndAssert(DataFormat inputFormat, DataFormat outputFormat, String outputContentName,
            String outputContentMediaType, String inputContent, String expectedOutputContent, List<String> mediaTypes,
            List<String> filePatterns, List<Integer> contentIndexes, boolean retainExistingContent) {
        ConvertParameters params = new ConvertParameters();
        params.setInputFormat(inputFormat);
        params.setOutputFormat(outputFormat);
        params.setMediaTypes(mediaTypes);
        params.setFilePatterns(filePatterns);
        params.setContentIndexes(contentIndexes);
        params.setRetainExistingContent(retainExistingContent);

        ActionContent content = runner.saveContent(inputContent, "example.json", "application/json");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert transformResultAssert = TransformResultAssert.assertThat(result);
        int index = 0;

        if (retainExistingContent) {
            for (ActionContent c : input.content()) {
                transformResultAssert.hasContentMatchingAt(index++, c.getName(), c.getMediaType(), c.loadBytes());
            }
        }

        transformResultAssert.hasContentMatchingAt(index, outputContentName, outputContentMediaType,
                expectedOutputContent);
    }
}
