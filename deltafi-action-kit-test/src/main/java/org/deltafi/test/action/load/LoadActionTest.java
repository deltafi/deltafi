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
package org.deltafi.test.action.load;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.ReinjectResult;
import org.deltafi.common.types.*;
import org.deltafi.test.action.Child;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.ActionTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
public abstract class LoadActionTest extends ActionTest {

    private Domain getDomain(List<Domain> domains, String name) {
        return domains.stream().filter(
                d -> d.getName().equals(name)).findFirst().orElseGet(() ->
                Assertions.fail("Could not find domain named " + name + " to normalize domain data"));
    }

    public void assertLoadResult(LoadActionTestCase testCase, LoadResult loadResult) {
        LoadResult expectedResult = new LoadResult(context(), List.of(new ActionContent(testCase.getExpectedContent(), context().getContentStorageService())));
        expectedResult.addMetadata(testCase.getResultMetadata());
        if(testCase.getOutputDomain()!=null) {
            // Load text string as the file to domain... for each entry in the map
            testCase.getOutputDomain().forEach((key, value) -> {
                byte[] domainContent = getTestResourceBytesOrNull(testCase.getTestName(), key);
                String output = domainContent==null ? null : new String(domainContent, StandardCharsets.UTF_8);
                expectedResult.addDomain(key.startsWith("domain.") ? key.substring(7) : key,
                        output, value);
            });

            // Let's try to normalize the names of the domains so that we don't fail randomly because the map's
            // keys were added in a different order...
            if(expectedResult.getDomains().size()!=loadResult.getDomains().size()) {
                Assertions.fail("Expected domains size " + expectedResult.getDomains().size() + " does not match actual Domains size " + loadResult.getDomains().size());
            }
            final List<Domain> expectedDomains = expectedResult.getDomains();
            List<Domain> domains = loadResult.getDomains().stream().map(Domain::getName).map(
                    n -> getDomain(expectedDomains, n)).toList();
            expectedDomains.clear();
            expectedDomains.addAll(domains);
        }

        List<byte[]> expectedContent = Collections.emptyList();
        if(!testCase.getOutputs().isEmpty()) {
            expectedContent = getExpectedContentOutput(expectedResult, testCase, testCase.getOutputs());
        }

        ActionEvent expectedEvent = normalizeEvent(expectedResult.toEvent());
        ActionEvent outputEvent = normalizeEvent(loadResult.toEvent());
        Assertions.assertEquals(expectedEvent, outputEvent);

        // TODO Check various ways to check contents
        // expectedContent should be a list of byte[], let's get the list of content and grab bytes to recreate
        List<byte[]> actualContent = loadResult.getContent().stream().map(ActionContent::loadBytes).toList();

        assertContentIsEqual(expectedContent, actualContent);
    }

    public void assertSplitResult(LoadActionTestCase testCase, ReinjectResult reinjectResult) {
        ReinjectResult expectedResult = new ReinjectResult(context());
        testCase.getOutputs().forEach(c -> {
            Assertions.assertTrue(c instanceof Child);
            Child child = (Child) c;
            String name = child.getName().startsWith("split.") ? child.getName().substring(6) : child.getName();
            expectedResult.addChild(name, child.getFlow(),
                    getContents(Collections.singletonList(
                            IOContent.builder().name(child.getName()).contentType(child.getContentType()).metadata(child.getMetadata()).build()
                    ), testCase, "split."), child.getMetadata());
        });

        ActionEvent expectedEvent = normalizeEvent(expectedResult.toEvent());
        ActionEvent outputEvent = normalizeEvent(reinjectResult.toEvent());
        Assertions.assertEquals(expectedEvent, outputEvent);
    }

    public void execute(LoadActionTestCase testCase) {
        if(testCase.getExpectedResultType()==LoadResult.class) {
            executeLoadResult(testCase);
        }
        else if(testCase.getExpectedResultType()== ReinjectResult.class) {
            executeLoadSplitResult(testCase);
        }
        else {
            super.execute(testCase);
        }
    }

    public void executeLoadResult(LoadActionTestCase loadActionTestCase) {
        LoadResult result = execute(loadActionTestCase, LoadResult.class);
        assertLoadResult(loadActionTestCase, result);
    }

    public void executeLoadSplitResult(LoadActionTestCase loadActionTestCase) {
        ReinjectResult result = execute(loadActionTestCase, ReinjectResult.class);
        assertSplitResult(loadActionTestCase, result);
    }
}
