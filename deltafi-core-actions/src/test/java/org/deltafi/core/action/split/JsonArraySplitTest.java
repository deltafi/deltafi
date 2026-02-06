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
package org.deltafi.core.action.split;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;

class JsonArraySplitTest {

    static final String VALID_LOG_FILE = """
            {"sev":"INFO","msg":"Message"}
            
            {"sev":"ERROR","msg":"Error"}
            """;

    static final String INVALID_LOG_FORMAT = """
            {"sev":"INFO","msg":"Message"}
            {"partial":
            {"sev":"ERROR","msg":"Error"}
            """;

    static final String INVALID_LOG_NOT_AN_OBJECT = """
            {"sev":"INFO","msg":"Message"}
            {"sev":"ERROR","msg":"Error"}
            "bogus"
            """;

    static final String VALID_JSON_ARRAY = """
            [
              {
                "name": "Sam",
                "age": 21
              },
              {
                "category": "pet",
                "type": "cat"
              }
            ]""";

    static final String VALID_JSON_OBJECT = """
                         {
              "category": "pet",
              "type": "cat"
            }""";


    JsonArraySplit action = new JsonArraySplit();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testJsonArraySplit() {
        String output1 = """
                {"name":"Sam","age":21}""";
        String output2 = """
                {"category":"pet","type":"cat"}""";

        splitArray(VALID_JSON_ARRAY, List.of(output1, output2),
                true, true, null);
    }

    @Test
    void testSplitJsonPerLine() {
        String output1 = """
                {"sev":"INFO","msg":"Message"}""";
        String output2 = """
                {"sev":"ERROR","msg":"Error"}""";

        splitArray(VALID_LOG_FILE, List.of(output1, output2),
                false, true, null);
    }

    @Test
    void testSplitJsonPerLineWithSuffix() {
        String output1 = """
                {"sev":"INFO","msg":"Message"}""";
        String output2 = """
                {"sev":"ERROR","msg":"Error"}""";

        splitArray(VALID_LOG_FILE, List.of(output1, output2),
                false, true, "-");
    }

    @Test
    void testDoNotSplitJsonPerLine() {
        splitArray(VALID_LOG_FILE, List.of(VALID_LOG_FILE),
                false, true,
                false, null);
    }

    @Test
    void testPassThroughButDoNotSplitOrVerifyEveryLine() {
        splitArray(INVALID_LOG_FORMAT, List.of(INVALID_LOG_FORMAT),
                false, true,
                false, null);
    }

    @Test
    void testInvalidSplitFormatting() {
        error(INVALID_LOG_FORMAT, false, true,
                "JSON parse error on line 2");
    }

    @Test
    void testInvalidSplitNotAnObject() {
        error(INVALID_LOG_NOT_AN_OBJECT, false, true,
                "JSON object not found on line 3");
    }

    @Test
    void testPassThroughEmptyContent() {
        splitArray("", List.of(""),
                true, true, null);
    }

    @Test
    void testEmptyContentErrorForFalsePassThrough() {
        error("",
                false, true,
                "Not a JSON object or array");
    }

    @Test
    void testEmptyArray() {
        splitArray("[]", List.of(),
                true, true, null);
    }

    @Test
    void testSingleElement() {
        String output = "{\"name\":\"Sam\",\"age\":21}";
        splitArray("{\"name\":\"Sam\",\"age\":21}", List.of(output),
                true, true, null);
    }

    @Test
    void testSingleElementArray() {
        String output = "{\"name\":\"Sam\",\"age\":21}";
        splitArray("[{\"name\":\"Sam\",\"age\":21}]", List.of(output),
                true, true, null);
    }

    @Test
    void testSplitWithSuffix() {
        String output1 = """
                {"name":"Sam","age":21}""";
        String output2 = """
                {"category":"pet","type":"cat"}""";

        splitArray(VALID_JSON_ARRAY, List.of(output1, output2),
                true, true, ":");
    }

    @Test
    void passThroughTextual() {
        String textualJson = VALID_JSON_OBJECT.replace('{', ' ');
        splitArray(textualJson, List.of(textualJson),
                true, true, null);
    }

    @Test
    void passThroughXml() {
        String xmlIsNotJson = "<a>b</a>";
        splitArray(xmlIsNotJson, List.of(xmlIsNotJson),
                true, true, null);
    }

    @Test
    void passThroughJsonButNotArray() {
        splitArray(VALID_JSON_OBJECT, List.of(VALID_JSON_OBJECT),
                true, true, null);
    }

    @Test
    void errorXml() {
        String xmlIsNotJson = "<a>b</a>";
        error(xmlIsNotJson, false, true,
                "Could not parse JSON content");
    }

    @Test
    void errorTextualJson() {
        String invalidJson = VALID_JSON_OBJECT.replace('{', ' ');
        error(invalidJson, false, true,
                "Not a JSON object or array");
    }

    @Test
    void errorNotArray() {
        error(VALID_JSON_OBJECT, true, false,
                "Content is not a JSON array");
    }

    @Test
    void errorNotArray2() {
        String notAnArray = VALID_JSON_ARRAY.replace('[', ' ');
        error(notAnArray, true, false,
                "Content is not a JSON array");
    }

    @Test
    void errorMultipleContents() {
        JsonArraySplitParameters params = new JsonArraySplitParameters();
        ActionContent content1 = runner.saveContent(VALID_JSON_ARRAY, "one.json", "application/json");
        ActionContent content2 = runner.saveContent(VALID_JSON_ARRAY, "two.json", "application/json");
        TransformInput input = TransformInput.builder().content(List.of(content1, content2)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ErrorResultAssert.assertThat(result).hasCause("Expected one content but received 2");
    }

    @Test
    void errorNoContents() {
        JsonArraySplitParameters params = new JsonArraySplitParameters();
        TransformInput input = TransformInput.builder().content(List.of()).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ErrorResultAssert.assertThat(result).hasCause("Expected one content but received 0");
    }

    private void splitArray(String inputContent,
                            List<String> expectedOutput,
                            boolean passThroughNonJson,
                            boolean passThroughNonJsonArray,
                            String suffix) {
        splitArray(inputContent, expectedOutput,
                passThroughNonJson, passThroughNonJsonArray, true, suffix);
    }

    private void splitArray(String inputContent,
                            List<String> expectedOutput,
                            boolean passThroughNonJson,
                            boolean passThroughNonJsonArray,
                            boolean splitOneObjectPerLine,
                            String suffix) {
        JsonArraySplitParameters params = new JsonArraySplitParameters();
        params.setPassThroughNonJson(passThroughNonJson);
        params.setPassThroughNonJsonArray(passThroughNonJsonArray);
        params.setSplitOneObjectPerLine(splitOneObjectPerLine);
        params.setContentSuffix(suffix);

        String inputName = "input.json";

        ActionContent content = runner.saveContent(inputContent, inputName, "application/json");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert transformResultAssert = TransformResultAssert.assertThat(result);
        transformResultAssert.hasContentCount(expectedOutput.size());
        for (int index = 0; index < expectedOutput.size(); ++index) {
            String outputContentName = inputName;
            if (suffix != null) {
                outputContentName += suffix + index;
            }

            transformResultAssert.hasContentMatchingAt(index, outputContentName,
                    MediaType.APPLICATION_JSON, expectedOutput.get(index));
        }
    }

    private void error(String inputContent,
                       boolean passThroughNonJson,
                       boolean passThroughNonJsonArray,
                       String error) {
        JsonArraySplitParameters params = new JsonArraySplitParameters();
        params.setPassThroughNonJson(passThroughNonJson);
        params.setPassThroughNonJsonArray(passThroughNonJsonArray);

        String inputName = "input.json";

        ActionContent content = runner.saveContent(inputContent, inputName, "application/json");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ErrorResultAssert errorResultAssert = ErrorResultAssert.assertThat(result);
        errorResultAssert.hasCause(error);
    }

}
