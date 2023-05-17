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
package org.deltafi.test.action.load;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.ReinjectResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.Content;
import org.deltafi.test.action.Child;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.TestCaseBase;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class LoadActionTestCase extends TestCaseBase<LoadAction<? extends ActionParameters>> {

    private Content expectedContent;

    private String sessionId;

    private Map<String, String> outputDomain;

    public static abstract class LoadActionTestCaseBuilder<C extends LoadActionTestCase, B extends LoadActionTestCase.LoadActionTestCaseBuilder<C, B>> extends TestCaseBase.TestCaseBaseBuilder<LoadAction<? extends ActionParameters>, C, B> {
        public B expectLoadResult(List<IOContent> outputs) {
            return expectLoadResult(outputs, Collections.emptyMap(), Collections.emptyMap());
        }

        public B expectLoadResult(List<IOContent> outputs, Map<String, String> metadata) {
            return expectLoadResult(outputs, metadata, Collections.emptyMap());
        }

        public B expectLoadResult(List<IOContent> outputs, Map<String, String> metadata, Map<String, String> domains) {
            expectedResultType(LoadResult.class);
            outputs(outputs);
            outputDomain(domains);
            resultMetadata(metadata);

            return self();
        }

        public B expectSplitResult(List<Child> children) {
            expectedResultType(ReinjectResult.class);
            outputs(children);

            return self();
        }
    }
}