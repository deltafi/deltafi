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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.ErrorParameters;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class ErrorByFiatTransformActionTest {
    @Test
    void errors() {
        ErrorByFiatTransformAction action = new ErrorByFiatTransformAction();

        TransformResultType transformResultType =
                action.transform(new ActionContext(), new ErrorParameters(), TransformInput.builder().build());
        assertErrorResult(transformResultType).hasCause("Errored by fiat");

        ErrorParameters errorParameters = new ErrorParameters();
        errorParameters.setMessage("Test error message");
        transformResultType = action.transform(new ActionContext(), errorParameters, TransformInput.builder().build());
        assertErrorResult(transformResultType).hasCause("Test error message");
    }

    @Test
    void errorsOnMetadata() {
        ErrorByFiatTransformAction action = new ErrorByFiatTransformAction();

        ErrorParameters errorParameters = new ErrorParameters();
        errorParameters.setMetadataTrigger("metadata-trigger-key");
        TransformResultType transformResultType = action.transform(new ActionContext(), errorParameters,
                TransformInput.builder().metadata(Map.of("metadata-trigger-key", "Test error message")).build());
        assertErrorResult(transformResultType).hasCause("Test error message");
    }

    @Test
    void passesThroughOnMetadataNotPresent() {
        ErrorByFiatTransformAction action = new ErrorByFiatTransformAction();

        ErrorParameters errorParameters = new ErrorParameters();
        errorParameters.setMetadataTrigger("non-matching-key");
        ActionContext context = new ActionContext();
        ActionContent actionContent = ActionContent.emptyContent(context, "empty", MediaType.TEXT_PLAIN);
        TransformResultType transformResultType = action.transform(context, errorParameters,
                TransformInput.builder().metadata(Map.of("metadata-trigger-key", "Test error message"))
                        .content(List.of(actionContent)).build());
        assertTransformResult(transformResultType).hasContentMatching(actionContent::equals);
    }
}
