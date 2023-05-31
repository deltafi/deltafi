/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.test.action.format;

import lombok.SneakyThrows;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.types.DeltaFileMessage;
import org.deltafi.common.types.Domain;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.ActionTest;
import org.deltafi.test.action.TestCaseBase;
import org.junit.jupiter.api.Assertions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FormatActionTest extends ActionTest {

    @SneakyThrows
    public void execute(FormatActionTestCase testCase) {
        if(testCase.getExpectedResultType() == FormatResult.class) {
            FormatResult result = execute(testCase, FormatResult.class);
            assertFormatResult(testCase, result);
        }
        else {
            super.execute(testCase);
        }
    }

    @Override
    protected void beforeExecuteAction(DeltaFileMessage deltaFileMessage, TestCaseBase<?> testCase) {
        // Add domains and enrichments to deltaFile
        FormatActionTestCase formatActionTestCase = (FormatActionTestCase) testCase;

        deltaFileMessage.setEnrichments(
            formatActionTestCase.getEnrichments().stream().map(this::readEnrichment).toList()
        );

        formatActionTestCase.getInputDomains().forEach((key, value) -> {
            byte[] content = getTestResourceBytesOrNull(formatActionTestCase.getTestName(), key);
            String output = content==null ? null : new String(content, StandardCharsets.UTF_8);
            String domainName = key.startsWith("domain.") ? key.substring(7) : key;
            deltaFileMessage.getDomains().add(new Domain(domainName, output, value));
        });
    }

    private org.deltafi.common.types.Enrichment readEnrichment(org.deltafi.test.action.Enrichment enrichment) {
        return org.deltafi.common.types.Enrichment.newBuilder()
                .value(enrichment.getValue())
                .name(enrichment.getName())
                .mediaType(enrichment.getContentType())
                .build();
    }

    public void assertFormatResult(FormatActionTestCase testCase, FormatResult result) {
        IOContent formatOutput = testCase.getOutputs().get(0);

        Assertions.assertEquals(formatOutput.getMetadata(), result.getMetadata());
        Assertions.assertEquals(formatOutput.getContentType(), result.getContent().getMediaType());
        Assertions.assertEquals(formatOutput.getName(), result.getContent().getName());

        byte[] actualContent = null;
        try (InputStream actualContentInputstream = result.getContent().loadInputStream()) {
            actualContent = actualContentInputstream.readAllBytes();
        }
        catch(Throwable t) {
            Assertions.fail("Unable to read content for actual comparisons", t);
        }

        ActionContent expectedContent = getContents(testCase.getOutputs(), testCase, "output.").get(0);
        try {
            byte[] expectedContentBytes = expectedContent.loadBytes();
            Assertions.assertArrayEquals(expectedContentBytes, actualContent);
        }
        catch(Throwable t) {
            Assertions.fail("Unable to read expected content for comparison", t);
        }
    }
}
