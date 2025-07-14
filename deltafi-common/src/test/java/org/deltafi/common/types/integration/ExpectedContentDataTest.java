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
package org.deltafi.common.types.integration;

import org.deltafi.common.types.KeyValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpectedContentDataTest {

    private static final String DID = "123";
    private static final String PARENT_DID = "456";

    @Test
    void testValidation() {
        // missing name and data
        ExpectedContentData testObj = ExpectedContentData.builder()
                .build();
        List<String> expectedErrors = List.of(
                "ExpectedContentData missing 'name'",
                "ExpectedContentData missing 'value' or 'contains'"
        );
        assertEquals(expectedErrors, testObj.validate());

        // missing data
        testObj = ExpectedContentData.builder()
                .name("myName")
                .build();
        expectedErrors = List.of(
                "ExpectedContentData missing 'value' or 'contains'"
        );
        assertEquals(expectedErrors, testObj.validate());

        // empty value - still missing data
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value("")
                .build();
        expectedErrors = List.of(
                "ExpectedContentData missing 'value' or 'contains'"
        );
        assertEquals(expectedErrors, testObj.validate());

        // empty list - still missing data
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(Collections.emptyList())
                .build();
        expectedErrors = List.of(
                "ExpectedContentData missing 'value' or 'contains'"
        );
        assertEquals(expectedErrors, testObj.validate());

        // cannot use both data fields
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("abcd"))
                .value("efgh")
                .build();
        expectedErrors = List.of(
                "ExpectedContentData must contain only one of 'value' or 'contains'"
        );
        assertEquals(expectedErrors, testObj.validate());

        // good
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("abcd"))
                .build();
        assertTrue(testObj.validate().isEmpty());

        // good
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value("efgh")
                .build();
        assertTrue(testObj.validate().isEmpty());
    }

    @Test
    void testBase64() {
        // Good encoding. generated using: echo "efgh" | base64
        String encoded = "ZWZnaAo=";
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .value(encoded)
                .base64Encoded(true)
                .build();
        assertTrue(testObj.validate().isEmpty());

        // Bad encoding
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(";ZZZ;") // not valid
                .base64Encoded(true)
                .build();
        List<String> expectedErrors = List.of(
                "Failed to base64-decode: ;ZZZ;"
        );
        assertEquals(expectedErrors, testObj.validate());
    }

    @Test
    void testValidateExtraSubstitutions() {
        // Good
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .value("efgh")
                .extraSubstitutions(List.of(new KeyValue("this", "that")))
                .build();
        assertTrue(testObj.validate().isEmpty());

        // key cannot be empty
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value("efgh")
                .extraSubstitutions(List.of(new KeyValue("", "that")))
                .build();
        List<String> expectedErrors = List.of(
                "Invalid extraSubstitutions key"
        );
        assertEquals(expectedErrors, testObj.validate());
    }

    @Test
    void testNormalize() {
        String initialValue = "aaa {{DID}} \n {{PARENT_DID}}-bbb";
        String ignoreWhitespace = "aaa{{DID}}{{PARENT_DID}}-bbb";
        String macroSubstitutions = "aaa 123 \n 456-bbb";
        String withMacroAndIgnoreWhitespace = "aaa123456-bbb";

        // No substitutions
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .value(initialValue)
                .build();
        assertTrue(testObj.validate().isEmpty());
        assertEquals(initialValue, testObj.normalize(DID, PARENT_DID));

        // Ignore whitespace
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(initialValue)
                .ignoreWhitespace(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        assertEquals(ignoreWhitespace, testObj.normalize(DID, PARENT_DID));

        // Macro substitutions
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(initialValue)
                .macroSubstitutions(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        assertEquals(macroSubstitutions, testObj.normalize(DID, PARENT_DID));

        // Macro substitutions and ignore whitespace
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(initialValue)
                .macroSubstitutions(true)
                .ignoreWhitespace(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        assertEquals(withMacroAndIgnoreWhitespace, testObj.normalize(DID, PARENT_DID));

        // Macro substitutions,  ignore whitespace, and encoded
        byte[] encodedBytes = Base64.getEncoder().encode(initialValue.getBytes());
        String encodedValue = new String(encodedBytes, StandardCharsets.UTF_8);
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(encodedValue)
                .macroSubstitutions(true)
                .ignoreWhitespace(true)
                .base64Encoded(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        assertEquals(withMacroAndIgnoreWhitespace, testObj.normalize(DID, PARENT_DID));
    }

    @Test
    void testCheckIfEquivalentFor_Contains() {
        // Good; contains both
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("aaa", "bbb"))
                .build();
        assertTrue(testObj.validate().isEmpty());
        String error = testObj.checkIfEquivalent("bbb,aaa", DID, null);
        assertNull(error);

        // extraSubstitutions not used with 'contains'
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("aaa", "bbb"))
                .extraSubstitutions(List.of(new KeyValue("aaa", "ddd")))
                .build();
        assertTrue(testObj.validate().isEmpty());
        error = testObj.checkIfEquivalent("bbb,aaa", DID, null);
        assertNull(error);

        // ignoreWhitespace not used with 'contains'
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("abcd"))
                .ignoreWhitespace(true) // not applicable to contains
                .build();
        assertTrue(testObj.validate().isEmpty());
        error = testObj.checkIfEquivalent("a b c d", DID, null);
        assertEquals("Missing expected content: abcd", error);

        // Missing ccc
        testObj = ExpectedContentData.builder()
                .name("myName")
                .contains(List.of("aaa", "ccc"))
                .ignoreWhitespace(true) // not applicable to contains
                .build();
        assertTrue(testObj.validate().isEmpty());
        error = testObj.checkIfEquivalent("bbb,aaa", DID, null);
        assertEquals("Missing expected content: ccc", error);
    }

    @Test
    void testCheckIfEquivalent_Value() {
        String initialValue = "aaa {{DID}} {{PARENT_DID}}-bbb";
        String inputWithExtraWhitespace = "aaa  123  456-bbb";
        byte[] encodedBytes = Base64.getEncoder().encode(initialValue.getBytes());
        String encodedValue = new String(encodedBytes, StandardCharsets.UTF_8);

        // Macro substitutions,  ignore whitespace, and encoded
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .value(encodedValue)
                .macroSubstitutions(true)
                .ignoreWhitespace(true)
                .base64Encoded(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        String error = testObj.checkIfEquivalent(inputWithExtraWhitespace, DID, PARENT_DID);
        assertNull(error);

        // Will not match because parentDid is null
        error = testObj.checkIfEquivalent(inputWithExtraWhitespace, DID, null);
        assertEquals("[myName] Expected  content to be 'aaa123{{PARENT_DID}}-bbb', but was 'aaa123456-bbb'", error);

        // Will not match because ignoreWhitespace is false
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value(encodedValue)
                .macroSubstitutions(true)
                .ignoreWhitespace(false)
                .base64Encoded(true)
                .build();
        assertTrue(testObj.validate().isEmpty());
        error = testObj.checkIfEquivalent(inputWithExtraWhitespace, DID, PARENT_DID);
        assertEquals("[myName] Expected  content to be 'aaa 123 456-bbb', but was 'aaa  123  456-bbb'", error);
    }

    @Test
    void testExtraSubstitutions() {
        // Matches with extraSubstitutions
        ExpectedContentData testObj = ExpectedContentData.builder()
                .name("myName")
                .value("aaa bbb ccc")
                .extraSubstitutions(List.of(new KeyValue("ddd", "bbb")))
                .build();
        assertTrue(testObj.validate().isEmpty());
        String error = testObj.checkIfEquivalent("aaa ddd ccc", DID, PARENT_DID);
        assertNull(error);

        // Matches with extraSubstitutions using a regex
        testObj = ExpectedContentData.builder()
                .name("myName")
                .value("aaa bbb ccc")
                .extraSubstitutions(List.of(new KeyValue("this=\\d{2,4}", "bbb")))
                .build();
        assertTrue(testObj.validate().isEmpty());
        error = testObj.checkIfEquivalent("aaa this=123 ccc", DID, PARENT_DID);
        assertNull(error);

        // Will not match because regex replace is not satisfied
        error = testObj.checkIfEquivalent("aaa this=1 ccc", DID, PARENT_DID);
        assertEquals("[myName] Expected  content to be 'aaa bbb ccc', but was 'aaa this=1 ccc'", error);
    }
}
