/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: GraphQL datafetcher for SpEL expression testing and validation.
// ABOUTME: Exposes mutations for testing expressions against sample data.
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.generated.types.SpelExpressionMode;
import org.deltafi.core.generated.types.SpelTestDataInput;
import org.deltafi.core.generated.types.SpelTestResult;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.SpelTestService;

@DgsComponent
@RequiredArgsConstructor
public class SpelDatafetcher {

    private final SpelTestService spelTestService;

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public SpelTestResult testSpelExpression(
            @InputArgument String expression,
            @InputArgument SpelExpressionMode mode,
            @InputArgument SpelTestDataInput testData) {
        return spelTestService.testExpression(expression, mode, testData);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public SpelTestResult validateSpelExpression(
            @InputArgument String expression,
            @InputArgument SpelExpressionMode mode) {
        return spelTestService.validateExpression(expression, mode);
    }
}
