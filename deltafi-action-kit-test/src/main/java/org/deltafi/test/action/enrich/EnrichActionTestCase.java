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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.test.action.Enrichment;
import org.deltafi.test.action.TestCaseBase;

import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class EnrichActionTestCase extends TestCaseBase<EnrichAction<? extends ActionParameters>> {

    Map<String, String> indexedMetadata;

    public static abstract class EnrichActionTestCaseBuilder<C extends EnrichActionTestCase, B extends EnrichActionTestCase.EnrichActionTestCaseBuilder<C, B>> extends TestCaseBase.TestCaseBaseBuilder<EnrichAction<? extends ActionParameters>, C, B> {
        public B expectEnrichResult(List<Enrichment> enrichments, Map<String, String> indexedMetadata) {
            expectedResultType(EnrichResult.class);
            outputs(enrichments);
            indexedMetadata(indexedMetadata);

            return self();
        }
    }
}
