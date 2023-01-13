/**
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
package org.deltafi.test.action.enrich;

import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.common.types.Enrichment;
import org.deltafi.test.action.ActionTest;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.stream.Collectors;

public class EnrichActionTest extends ActionTest {

    public void execute(EnrichActionTestCase testCase) {
        if(testCase.getExpectedResultType() == EnrichResult.class) {
            EnrichResult result = execute(testCase, EnrichResult.class);
            assertEnrichmentResult(testCase, result);
        }
        else {
            super.execute(testCase);
        }
    }

    private void assertEnrichmentResult(EnrichActionTestCase testCase, EnrichResult result) {
        List<Enrichment> expectedEnrichments = testCase.getOutputs().stream()
                .map(org.deltafi.test.action.Enrichment.class::cast)
                .map(enrichment -> new Enrichment(enrichment.getName(), enrichment.getValue(), enrichment.getContentType()))
                .collect(Collectors.toList());

        Assertions.assertArrayEquals(expectedEnrichments.toArray(), result.getEnrichments().toArray());

        Assertions.assertEquals(testCase.getIndexedMetadata(), result.getIndexedMetadata());
    }
}
