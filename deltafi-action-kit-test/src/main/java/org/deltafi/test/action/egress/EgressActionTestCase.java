/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.test.action.egress;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.test.action.TestCaseBase;

/**
 * @deprecated Use the DeltaFiTestRunner to set up the test and run the action directly.
 * The result can be verified using {@link org.deltafi.test.asserters.ActionResultAssertions}.
 */
@Deprecated
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class EgressActionTestCase extends TestCaseBase<EgressAction<? extends ActionParameters>> {
}
