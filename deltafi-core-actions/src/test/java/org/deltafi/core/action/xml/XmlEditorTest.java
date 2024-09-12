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
package org.deltafi.core.action.xml;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.deltafi.test.asserters.ActionResultAssertions.*;


class XmlEditorTest {

    private static final String XML_VERSION_LINE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    // matches any character, including newlines
    private static final String REGEX_ANY_CHAR = "[\\s\\S]*";

    /* Note  that tags 'value' and 'length' are used for different parent tags, so can test XPath location based
       operations affect the correct path (and not other identical tag names).
     */
    private static final String INPUT_DATA = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ships>
                   <ship>
                      <model>T-65 X-Wing</model>
                      <type>starfighter</type>
                      <length>
                         <value>13.4</value>
                         <units>meters</units>
                      </length>
                      <maximum_atmospheric_speed>
                         <value>1,050</value>
                         <units>kph</units>
                      </maximum_atmospheric_speed>
                   </ship>
                   <ship>
                      <model>YT-1300fp light freighter</model>
                      <type>light freighter</type>
                      <length>
                         <value>34</value>
                         <units>meters</units>
                      </length>
                      <maximum_atmospheric_speed>
                         <value>1,200</value>
                         <units>kph</units>
                      </maximum_atmospheric_speed>
                   </ship>
                   <ship>
                      <model>Delta-7</model>
                      <type>starfighter</type>
                      <length>
                         <value>8</value>
                         <units>meters</units>
                      </length>
                      <maximum_atmospheric_speed>
                         <value>1,260</value>
                         <units>kph</units>
                      </maximum_atmospheric_speed>
                   </ship>
                </ships>
                """.trim();
    
    XmlEditor action = new XmlEditor();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();
    ActionContext context = runner.actionContext();


    /*
     *
     * Test "modify" operations
     *
     */

    @Test
    void testModifyOperationCommandRenameTagSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("renameTag units measure"),
                INPUT_DATA.replace("units>", "measure>"));

        // match, path specifies root tag only
        performXmlTransformTestSuccess(
                List.of("renameTag /ships crafts"),
                INPUT_DATA.replace("ships>", "crafts>"));

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("renameTag /ships/ship/length/units measure"),
                INPUT_DATA.replace("<units>meters</units>", "<measure>meters</measure>"));

        // no match
        performXmlTransformTestSuccess(
                List.of("renameTag not-exist something"),
                INPUT_DATA);
    }

    @Test
    void testModifyOperationCommandRenameTagError() {

        // no args
        performXmlTransformTestError(
                List.of("renameTag"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'renameTag' expected 2 arguments but had 0 arguments"));

        // missing one arg
        performXmlTransformTestError(
                List.of("renameTag something"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'renameTag' expected 2 arguments but had 1 arguments"));

        // too many args
        performXmlTransformTestError(
                List.of("renameTag something another etc"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'renameTag' expected 2 arguments but had 3 arguments"));

    }


    @Test
    void testModifyOperationCommandRemoveTagSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("removeTag units"),
                INPUT_DATA
                        .replace("<units>kph</units>", "")
                        .replace("<units>meters</units>", ""));

        // match, path specifies root tag only
        performXmlTransformTestSuccess(
                List.of("removeTag /ships"),
                XML_VERSION_LINE + "\n");

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("removeTag /ships/ship/length/units"),
                INPUT_DATA.replace("<units>meters</units>", ""));

        // no match
        performXmlTransformTestSuccess(
                List.of("removeTag not-exist"),
                INPUT_DATA);
    }


    @Test
    void testModifyOperationCommandRemoveTagError() {

        // no args
        performXmlTransformTestError(
                List.of("removeTag"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'removeTag' expected 1 arguments but had 0 arguments"));

        // too many args
        performXmlTransformTestError(
                List.of("removeTag arg1 arg2"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'removeTag' expected 1 arguments but had 2 arguments"));
    }

    @Test
    void testModifyOperationCommandReplaceTagSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("replaceTag units <measure>something</measure>"),
                INPUT_DATA.replace("<units>meters</units>", "<measure>something</measure>")
                         .replace("<units>kph</units>", "<measure>something</measure>"));

        // match, path specifies root tag only
        String replaceTagContent = "<people><person>Han</person></people>";
        StringBuilder sb = new StringBuilder();
        sb.append(XML_VERSION_LINE + "\n");
        sb.append(replaceTagContent);
        performXmlTransformTestSuccess(
                List.of("replaceTag /ships " + replaceTagContent),
                sb.toString());

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("replaceTag /ships/ship/length/units <measure>something</measure>"),
                INPUT_DATA.replace("<units>meters</units>", "<measure>something</measure>"));

        // match, with content using argument separator characters (space and comma) and XML tags
        performXmlTransformTestSuccess(
                List.of("replaceTag /ships/ship/length/units <units><unit>meters</unit><type>metric</type>" +
                        "<comment> some comments here, and there</comment></units>"),
                INPUT_DATA.replace("<units>meters</units>", "<units><unit>meters</unit><type>" +
                        "metric</type><comment> some comments here, and there</comment></units>"));

        // no match
        performXmlTransformTestSuccess(
                List.of("replaceTag not-exist <measure>something</measure>"),
                INPUT_DATA);

    }


    @Test
    void testModifyOperationCommandReplaceTagError() {

        // no args
        performXmlTransformTestError(
                List.of("replaceTag"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'replaceTag' expected 2 arguments but had 0 arguments"));

        // missing one arg
        performXmlTransformTestError(
                List.of("replaceTag something"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'replaceTag' expected 2 arguments but had 1 arguments"));


        // anything after the 2nd arg is assumed to be content for the replacement tag, so can't test too many args

        // bad xslt transform tested separately
    }


    @Test
    void testModifyOperationCommandReplaceTagContentSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("replaceTagContent units <measure>something</measure>"),
                INPUT_DATA.replace("<units>meters</units>", "<units><measure>something</measure></units>")
                        .replace("<units>kph</units>", "<units><measure>something</measure></units>"));

        // match, path specifies root tag only
        performXmlTransformTestSuccess(
                List.of("replaceTagContent /ships <people><person>Han</person></people>"),
                XML_VERSION_LINE + "\n" + "<ships><people><person>Han</person></people></ships>");

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("replaceTagContent /ships/ship/length/units <measure>something</measure>"),
                INPUT_DATA.replace("<units>meters</units>", "<units><measure>something</measure></units>"));

        // no match
        performXmlTransformTestSuccess(
                List.of("replaceTagContent not-exist <measure>something</measure>"),
                INPUT_DATA);
    }


    @Test
    void testModifyOperationCommandReplaceTagContentError() {

        // no args
        performXmlTransformTestError(
                List.of("replaceTagContent"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'replaceTagContent' expected 2 arguments but had 0 arguments"));

        // missing one arg
        performXmlTransformTestError(
                List.of("replaceTagContent something"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'replaceTagContent' expected 2 arguments but had 1 arguments"));

        // anything after the 2nd arg is assumed to be content for the replacement content, so can't test too many args

        // bad xslt transform tested separately
    }


    @Test
    void testModifyOperationCommandAppendChildSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("appendChild ships <generic_ship>something</generic_ship>"),
                INPUT_DATA.replace("</ships>", "<generic_ship>something</generic_ship>\n</ships>"));

        // match, path specifies root tag only
        performXmlTransformTestSuccess(
                List.of("appendChild /ships <generic_ship>something</generic_ship>"),
                INPUT_DATA.replace("</ships>", "<generic_ship>something</generic_ship>\n</ships>"));

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("appendChild /ships/ship <type>ship</type>"),
                INPUT_DATA
                        .replace("</maximum_atmospheric_speed>", "</maximum_atmospheric_speed>\n   <type>ship</type>")
                        .replace("   </ship>", "</ship>"));

        // no match
        performXmlTransformTestSuccess(
                List.of("appendChild not-exist <generic_ship>something</generic_ship>"),
                INPUT_DATA);

    }


    @Test
    void testModifyOperationCommandAppendChildError() {

        // no args
        performXmlTransformTestError(
                List.of("appendChild"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'appendChild' expected 2 arguments but had 0 arguments"));

        // missing one arg
        performXmlTransformTestError(
                List.of("appendChild something"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'appendChild' expected 2 arguments but had 1 arguments"));

        // anything after the 2nd arg is assumed to be content for the replacement content, so can't test too many args

        // bad xslt transform tested separately
    }


    @Test
    void testModifyOperationCommandPrependChildSuccess() {

        // match, no path
        performXmlTransformTestSuccess(
                List.of("prependChild ships <generic_ship>something</generic_ship>"),
                INPUT_DATA.replace("<ships>", "<ships>\n<generic_ship>something</generic_ship>"));

        // match, path specifies root tag only
        performXmlTransformTestSuccess(
                List.of("prependChild /ships <generic_ship>something</generic_ship>"),
                INPUT_DATA.replace("<ships>", "<ships>\n<generic_ship>something</generic_ship>"));

        // match, path specifies non-root tag
        performXmlTransformTestSuccess(
                List.of("prependChild /ships/ship <type>ship</type>"),
                INPUT_DATA.replace("<ship>", "<ship>\n<type>ship</type>"));

        // no match
        performXmlTransformTestSuccess(
                List.of("prependChild not-exist <generic_ship>something</generic_ship>"),
                INPUT_DATA);

    }


    @Test
    void testModifyOperationCommandPrependChildError() {

        // no args
        performXmlTransformTestError(
                List.of("prependChild"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'prependChild' expected 2 arguments but had 0 arguments"));

        // missing one arg
        performXmlTransformTestError(
                List.of("prependChild something"),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression for command 'prependChild' expected 2 arguments but had 1 arguments"));

        // anything after the 2nd arg is assumed to be content for the replacement content, so can't test too many args

        // bad xslt transform tested separately
    }


    @Test
    void testModifyOperationWithBadXsltError() {
        performXmlTransformTestError(
                List.of("replaceTag /ships/ship/length/units <malformed>expression<none>"),
                "Error creating XSLT from XML command expression at index 0 with command 'replaceTag'",
                createRegexStringMatchAnyBeforeAfter("Error creating XSLT transformer"));
    }


    @Test
    void testModifyOperationWithInvalidContentError() {
        performXmlTransformTestError(
                List.of("renameTag something else"),
                "Error applying 'modify' operation to content at index 0 using XML command expression at index 0 with command 'renameTag'",
                createRegexStringMatchAnyBeforeAfter("javax.xml.transform.TransformerException"),
                createBadContent());
    }


    @Test
    void testModifyOperationMultipleSuccess() {
        performXmlTransformTestSuccess(
                List.of("renameTag units measure",
                        "removeTag /ships/ship/length/measure"),
                INPUT_DATA
                        .replace("units>", "measure>")
                        .replace("<measure>meters</measure>", ""));
    }


    /*
     *
     * Test "screen" operations
     *
     */

    @ParameterizedTest
    @ValueSource(strings = {

            // no operator specified
            "doesnt-exist",         // root w/o path
            "/doesnt-exist",        // root w/ path
            "/ships/doesnt-exist",  // child w/ path

            // NOT operator
            "not ships",
            "not /ships",
            "not /ships/ship",

            // AND operator
               // 2 inputs
            "and doesnt-exist /not-found",
            "and doesnt-exist /ships",
            "and /ships doesnt-exist",
               // 3 inputs
            "and /doesnt-exist /not-found /not/found",
            "and /doesnt-exist /not-found /ships",
            "and /doesnt-exist /ships /not-found",
            "and /ships /doesnt-exist /not-found",
            "and /doesnt-exist /ships /ships/ship",
            "and /ships /doesnt-exist /ships/ship",
            "and /ships /ships/ship /doesnt-exist",

            // NAND operator
               // 2 inputs
            "nand ships /ships/ship",
            "nand /ships /ships/ship",
               // 3 inputs
            "nand /ships/ship/model /ships/ship/length /ships/ship/maximum_atmospheric_speed",

            // OR operator
               // 2 inputs
            "or doesnt-exist /not-found",
               // 3 inputs
            "or /doesnt-exist /not-found /not/found",

            // NOR operator
               // 2 inputs
            "nor doesnt-exist /ships",
            "nor /ships doesnt-exist",
            "nor /ships /ships/ship",
               // 3 inputs
            "nor /doesnt-exist /not-found /ships",
            "nor /doesnt-exist /ships /not-found",
            "nor /ships /doesnt-exist /not-found",
            "nor /doesnt-exist /ships/ship /ships",
            "nor /ships /doesnt-exist /ships/ship",
            "nor /ships/ship /ships /doesnt-exist",
            "nor /ships/ship /ships /ships/ship/model",

            // XOR operator
               // 2 inputs
            "xor doesnt-exist /not-found",
            "xor /ships/ship /ships",
               // 3 inputs
            "xor /doesnt-exist /not-found /still/not/found",
            "xor /ships/ship /ships /ships/ship/model",

            // XNOR
               // 2 inputs
            "xnor doesnt-exist /ships",
            "xnor /ships doesnt-exist",
               // 3 inputs
            "xnor doesnt-exist /not-found /ships",
            "xnor doesnt-exist /ships /not-found",
            "xnor /ships doesnt-exist /not-found",
            "xnor doesnt-exist /ships/ship /ships",
            "xnor /ships doesnt-exist /ships/ship",
            "xnor /ships/ship /ships doesnt-exist"
    })
    void testScreenOperationCommandFilterErrorOnTagNoMatchSuccess(String query) {
        performXmlTransformTestSuccess(
                List.of("filterOnTag " + query + " \"Filtering result because of xyz\""),
                INPUT_DATA);

        performXmlTransformTestSuccess(
                List.of("errorOnTag " + query + " \"Filtering result because of xyz\""),
                INPUT_DATA);
    }


    @Test
    void testMultipleScreenFilterOperations() {

        // first one hits
        performXmlTransformTestFilter(
                List.of("filterOnTag /ships/ship \"A ship was found\"",
                        "filterOnTag /ships/notfound \"A notfound was found\"",
                        "filterOnTag /ships/unknown \"An unknown was found\""),
                "A ship was found");

        // middle one hits
        performXmlTransformTestFilter(
                List.of("filterOnTag /ships/notfound \"A notfound was found\"",
                        "filterOnTag /ships/ship \"A ship was found\"",
                        "filterOnTag /ships/unknown \"An unknown was found\""),
                "A ship was found");

        // last one hits
        performXmlTransformTestFilter(
                List.of("filterOnTag /ships/notfound \"A notfound was found\"",
                        "filterOnTag /ships/unknown \"An unknown was found\"",
                        "filterOnTag /ships/ship \"A ship was found\""),
                "A ship was found");
    }


    @Test
    void testMultipleScreenErrorOperations() {

        // first one hits
        performXmlTransformTestError(
                List.of("errorOnTag /ships/ship \"A ship was found\"",
                        "errorOnTag /ships/notfound \"A notfound was found\"",
                        "errorOnTag /ships/unknown \"An unknown was found\""),
                "A ship was found");

        // middle one hits
        performXmlTransformTestError(
                List.of("errorOnTag /ships/notfound \"A notfound was found\"",
                        "errorOnTag /ships/ship \"A ship was found\"",
                        "errorOnTag /ships/unknown \"An unknown was found\""),
                "A ship was found");

        // last one hits
        performXmlTransformTestError(
                List.of("errorOnTag /ships/notfound \"A notfound was found\"",
                        "errorOnTag /ships/unknown \"An unknown was found\"",
                        "errorOnTag /ships/ship \"A ship was found\""),
                "A ship was found");
    }


    @ParameterizedTest
    @ValueSource(strings = {

            // no operator specified
            "ships",        // root w/o path
            "/ships",       // root w/ path
            "/ships/ship",  // child w/ path

            // NOT operator
            "not doesnt-exist",
            "not /doesnt-exist",
            "not /ships/doesnt-exist",

            // AND operator
               // 2 inputs
            "and ships /ships/ship",
            "and /ships /ships/ship",
               // 3 inputs
            "and /ships/ship/model /ships/ship/length /ships/ship/maximum_atmospheric_speed",

            // AND operator
               // 2 inputs
            "nand doesnt-exist /not-found",
            "nand doesnt-exist /ships",
            "nand /ships doesnt-exist",
               // 3 inputs
            "nand /doesnt-exist /not-found /not/found",
            "nand /doesnt-exist /not-found /ships",
            "nand /doesnt-exist /ships /not-found",
            "nand /ships /doesnt-exist /not-found",
            "nand /doesnt-exist /ships /ships/ship",
            "nand /ships /doesnt-exist /ships/ship",
            "nand /ships /ships/ship /doesnt-exist",

            // OR operator
               // 2 inputs
            "or doesnt-exist /ships",
            "or /ships doesnt-exist",
            "or /ships /ships/ship",
               // 3 inputs
            "or /doesnt-exist /not-found /ships",
            "or /doesnt-exist /ships /not-found",
            "or /ships /doesnt-exist /not-found",
            "or /doesnt-exist /ships/ship /ships",
            "or /ships /doesnt-exist /ships/ship",
            "or /ships/ship /ships /doesnt-exist",
            "or /ships/ship /ships /ships/ship/model",

            // NOR operator
               // 2 inputs
            "nor doesnt-exist /not-found",
               // 3 inputs
            "nor /doesnt-exist /not-found /not/found",

            // XOR operator
               // 2 inputs
            "xor /ships doesnt-exist",
            "xor doesnt-exist /ships",
               // 3 inputs
            "xor doesnt-exist /not-found /ships",
            "xor doesnt-exist /ships /not-found",
            "xor /ships doesnt-exist /not-found",
            "xor doesnt-exist /ships/ship /ships",
            "xor /ships doesnt-exist /ships/ship",
            "xor /ships /ships/ship doesnt-exist",

            // XNOR operator
               // 2 inputs
            "xnor /ships /ships/ship",
            "xnor /not-found doesnt-exist",
               // 3 inputs
            "xnor /ships /ships/ship /ships/ship/model",
            "xnor /not-found doesnt-exist /still/not/found"
    })
    void testScreenOperationCommandFilterOnTagMatchSuccess(String query) {
        performXmlTransformTestFilter(
                List.of("filterOnTag " + query + " \"Filtering result because of xyz\""),
                "Filtering result because of xyz");

        performXmlTransformTestError(
                List.of("errorOnTag " + query + " \"Erroring result because of xyz\""),
                "Erroring result because of xyz");
    }


    @ParameterizedTest
    @CsvSource({
            // no args
            "TOKEN_COMMAND,Command expression for command 'TOKEN_COMMAND' expected 2 arguments but had no arguments",

            // one arg that's not quoted
            "TOKEN_COMMAND arg, Command expression for command 'TOKEN_COMMAND' expected last argument to be a message in quotes but did not find a quote",

            // one arg that is quoted, missing other arg
            "TOKEN_COMMAND \"Filtering result because of xyz\",Command expression for command 'TOKEN_COMMAND' expected 2 or more arguments but had 1 argument",

            // error message is too short
            "TOKEN_COMMAND \"ab\",Command expression for command 'TOKEN_COMMAND' expected message to be at least 3 characters but was only 2 characters",

            // a 2nd quoted arg
            "TOKEN_COMMAND \"Filtering result because of xyz\" arg2,Command expression for command 'TOKEN_COMMAND' expected last argument to be a message in quotes but found at least one trailing character",

            // a trailing unquoted arg
            "TOKEN_COMMAND something \"An error message\" arg2,Command expression for command 'TOKEN_COMMAND' expected last argument to be a message in quotes but found at least one trailing character",

            // no operator with multiple arguments
            "TOKEN_COMMAND something another \"An error message\",Command expression for command 'TOKEN_COMMAND' specified 3 arguments but did not define an operator"
    })
    void testScreenOperationCommandFilterErrorOnTagOperatorNoneError(String expression, String expectedContext) {

        String tokenCommand = "TOKEN_COMMAND";

        String command = "filterOnTag";
        performXmlTransformTestError(
                List.of(expression.replace(tokenCommand, command)),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter(expectedContext.replace(tokenCommand, command)));

        command = "errorOnTag";
        performXmlTransformTestError(
                List.of(expression.replace(tokenCommand, command)),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter(expectedContext.replace(tokenCommand, command)));
    }


    @ParameterizedTest
    @CsvSource({
            // no args
            "TOKEN_COMMAND not,Command expression for command 'TOKEN_COMMAND' expected last argument to be a message in quotes but did not find a quote",

            // too many args
            "TOKEN_COMMAND not tag1 tag2 \"An error message\",Command expression for command 'TOKEN_COMMAND' using operator 'not' expected 2 argument but had 4 arguments"
    })
    void testScreenOperationCommandFilterErrorOnTagOperatorNotError(String expression, String expectedContext) {

        String tokenCommand = "TOKEN_COMMAND";

        String command = "filterOnTag";
        performXmlTransformTestError(
                List.of(expression.replace(tokenCommand, command)),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter(expectedContext.replace(tokenCommand, command)));

        command = "errorOnTag";
        performXmlTransformTestError(
                List.of(expression.replace(tokenCommand, command)),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter(expectedContext.replace(tokenCommand, command)));
    }


    // the error conditions here are generally independent of the multi-arg operators
    @ParameterizedTest
    @CsvSource({
            // AND operator
               // no args
            "TOKEN_COMMAND TOKEN_OPERATOR,Command expression for command 'TOKEN_COMMAND' expected last argument to be a message in quotes but did not find a quote",
            "TOKEN_COMMAND TOKEN_OPERATOR tag1 \"An error message\",Command expression for command 'TOKEN_COMMAND' specified 3 arguments but requires at least 4 arguments"
    })
    void testScreenOperationCommandFilterErrorOnTagOperatorWithMultiArgsError(String expression, String expectedContext) {

        String tokenCommand = "TOKEN_COMMAND";
        String tokenOperator = "TOKEN_OPERATOR";

        String[] operatorList = new String[]{"and", "nand", "or", "nor", "xor", "xnor"};

        for (String operator : operatorList) {

            String command = "filterOnTag";
            performXmlTransformTestError(
                    List.of(expression
                            .replace(tokenCommand, command)
                            .replace(tokenOperator, operator)),
                    "Error parsing XML command expression at index 0",
                    createRegexStringMatchAnyBeforeAfter(expectedContext
                            .replace(tokenCommand, command)
                            .replace(tokenOperator, operator)));

            command = "errorOnTag";
            performXmlTransformTestError(
                    List.of(expression
                            .replace(tokenCommand, command)
                            .replace(tokenOperator, operator)),
                    "Error parsing XML command expression at index 0",
                    createRegexStringMatchAnyBeforeAfter(expectedContext
                            .replace(tokenCommand, command)
                            .replace(tokenOperator, operator)));
        }
    }


    @Test
    void testScreenOperationWithInvalidContentError() {
        performXmlTransformTestError(
                List.of("filterOnTag something \"An error message\""),
                "Error applying 'screen' operation to content at index 0 using XML command expression at index 0 with command 'filterOnTag'",
                createRegexStringMatchAnyBeforeAfter("org.xml.sax.SAXParseException"),
                createBadContent());
    }


    /*
     *
     * Combined 'modify' and 'screen' operations
     *
     */

    @Test
    void testModifyScreenOperationsTriggerModify() {
        performXmlTransformTestSuccess(
                List.of("renameTag units measure",
                        "filterOnTag /ships/notfound \"A notfound was found\""),
                INPUT_DATA.replace("units>", "measure>"));
    }


    @Test
    void testModifyScreenOperationsTriggerScreen() {
        performXmlTransformTestFilter(
                List.of("renameTag units measure",
                        "filterOnTag /ships/ship \"A ship was found\""),
                "A ship was found");
    }


    /*
     *
     * Test generic commands
     *
     */

    @Test
    void testCommandExpressionUnknownCommandError() {

        // first expression
        performXmlTransformTestError(
                List.of("someCommand x"),
                "Error parsing XML command expression at index 0 with command 'someCommand'",
                createRegexStringMatchAnyBeforeAfter("x"));

        // second expression
        performXmlTransformTestError(
                List.of("renameTag something else",
                        "someCommand x "),
                "Error parsing XML command expression at index 1 with command 'someCommand'",
                createRegexStringMatchAnyBeforeAfter("Command expression with command 'someCommand' was not recognized"));

    }



    @Test
    void testCommandExpressionEmptyError() {

        // first expression empty = empty string
        performXmlTransformTestError(
                List.of(" "),
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression was empty"));

        // first expression empty = null
        List<String> listWithNull = new LinkedList<>();
        listWithNull.add(null);
        performXmlTransformTestError(
                listWithNull,
                "Error parsing XML command expression at index 0",
                createRegexStringMatchAnyBeforeAfter("Command expression was null"));

        // second expression empty
        performXmlTransformTestError(
                List.of("renameTag something else",
                        " "),
                "Error parsing XML command expression at index 1",
                createRegexStringMatchAnyBeforeAfter("Command expression was empty"));

    }


    @Test
    void testCommandExpressionSeparatorsSuccess() {

        // one space
        performXmlTransformTestSuccess(
                List.of("renameTag units measure"),
                INPUT_DATA.replace("units>", "measure>"));

        // multiple spaces
        performXmlTransformTestSuccess(
                List.of("renameTag     units    measure"),
                INPUT_DATA.replace("units>", "measure>"));

        // comma, no space
        performXmlTransformTestSuccess(
                List.of("renameTag,units,measure"),
                INPUT_DATA.replace("units>", "measure>"));

        // comma, one space
        performXmlTransformTestSuccess(
                List.of("renameTag, units, measure"),
                INPUT_DATA.replace("units>", "measure>"));

        // comma, many spaces
        performXmlTransformTestSuccess(
                List.of("renameTag,     units,       measure"),
                INPUT_DATA.replace("units>", "measure>"));
    }


    /*
     *
     * Test helpers
     *
     */

    ResultType performXmlTransformTest(List<String> xmlEditingCommandList, TransformInput input) {
        XmlEditorParameters params = new XmlEditorParameters();
        params.setXmlEditingCommands(xmlEditingCommandList);
        params.setContentIndexes(List.of(0));
        params.setFilePatterns(List.of("example.*"));

        return action.transform(context, params, input);
    }


    void performXmlTransformTestSuccess(List<String> xmlEditingCommandList, String expectedOutputData) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, createContent());

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, this::checkContentName)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo(expectedOutputData);
                    return true;
                });
    }


    void performXmlTransformTestFilter(List<String> xmlEditingCommandList, String expectedCause) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, createContent());

        assertFilterResult(result)
                .hasCause(expectedCause);
    }


    void performXmlTransformTestFilter(List<String> xmlEditingCommandList, String expectedCause,
                                       String expectedErrorContext) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, createContent());

        assertFilterResult(result)
                .hasCause(expectedCause)
                .hasContextLike(expectedErrorContext);
    }


    void performXmlTransformTestError(List<String> xmlEditingCommandList, String expectedCause,
                                      String expectedErrorContext) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, createContent());

        assertErrorResult(result)
                .hasCause(expectedCause)
                .hasContextLike(expectedErrorContext);
    }


    void performXmlTransformTestError(List<String> xmlEditingCommandList, String expectedCause) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, createContent());

        assertErrorResult(result)
                .hasCause(expectedCause);
    }


    // this test helper allows custom TransformInput to be defined
    void performXmlTransformTestError(List<String> xmlEditingCommandList, String expectedCause,
                                      String expectedErrorContext, TransformInput input) {

        ResultType result = performXmlTransformTest(xmlEditingCommandList, input);

        assertErrorResult(result)
                .hasCause(expectedCause)
                .hasContextLike(expectedErrorContext);
    }


    private boolean checkContentName(ActionContent content) {
        Assertions.assertThat(content.getName()).isEqualTo("example.xml");
        return true;
    }


    private TransformInput createContent() {
        ActionContent content = runner.saveContent(INPUT_DATA, "example.xml", "application/xml");
        return TransformInput.builder().content(List.of(content)).build();
    }


    private TransformInput createBadContent() {
        ActionContent content = runner.saveContent("INVALID", "example.xml", "application/xml");
        return TransformInput.builder().content(List.of(content)).build();
    }


    private String createRegexStringMatchAnyBeforeAfter(String data) {
        return REGEX_ANY_CHAR + data + REGEX_ANY_CHAR;
    }
}
