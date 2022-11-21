/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.content.ContentReference;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.ActionTest;
import org.junit.jupiter.api.Assertions;

import java.io.InputStream;

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

    public void assertFormatResult(FormatActionTestCase testCase, FormatResult result) {
        IOContent formatOutput = testCase.getOutputs().get(0);

        Assertions.assertEquals(formatOutput.getMetadata(), result.getMetadata());
        Assertions.assertEquals(formatOutput.getContentType(), result.getContentReference().getMediaType());
        Assertions.assertEquals(formatOutput.getName(), result.getFilename());

        byte[] actualContent = null;
        try (InputStream actualContentInputstream = contentStorageService.load(result.getContentReference())) {
            actualContent = actualContentInputstream.readAllBytes();
        }
        catch(Throwable t) {
            Assertions.fail("Unable to read content for actual comparisons", t);
        }

        ContentReference expectedReference = getContents(testCase.getOutputs(), testCase, "output.").get(0).getContentReference();
        byte[] expectedContent = null;
        try (InputStream expectedContentInputstream = contentStorageService.load(expectedReference)) {
            expectedContent = expectedContentInputstream.readAllBytes();
        }
        catch(Throwable t) {
            Assertions.fail("Unable to read expected content for comparison", t);
        }
        Assertions.assertArrayEquals(expectedContent, actualContent);
    }
}
