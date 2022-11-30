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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.test.action.Enrichment;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.TestCaseBase;

import java.util.Collections;
import java.util.List;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FormatActionTestCase extends TestCaseBase<FormatAction<? extends ActionParameters>> {

    @Builder.Default
    List<Enrichment> enrichments = Collections.emptyList();

    public static abstract class FormatActionTestCaseBuilder<C extends FormatActionTestCase, B extends FormatActionTestCase.FormatActionTestCaseBuilder<C, B>> extends TestCaseBase.TestCaseBaseBuilder<FormatAction<? extends ActionParameters>, C, B> {

        public B expectFormatAction(IOContent format) {
            expectedResultType(FormatResult.class);
            outputs(Collections.singletonList(format));

            return self();
        }
    }
}
