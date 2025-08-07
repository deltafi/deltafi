/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.warning;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

class WarningTest {
    @Test
    void warning() {
        Warning action = new Warning();
        TransformResultType transformResultType =
                action.transform(new ActionContext(), new WarningParameters(), TransformInput.builder().build());
        TransformResultAssert.assertThat(transformResultType).hasMessageSize(1)
                .hasWarning("Warning by fiat");

        WarningParameters params = new WarningParameters();
        params.setMessage("Test warn message");
        transformResultType = action.transform(new ActionContext(), params, TransformInput.builder().build());
        TransformResultAssert.assertThat(transformResultType).hasMessageSize(1)
                .hasWarning("Test warn message");
    }

    @Test
    void errorsOnMetadata() {
        Warning action = new Warning();

        WarningParameters params = new WarningParameters();
        params.setMetadataTrigger("metadata-trigger-key");
        TransformResultType transformResultType = action.transform(new ActionContext(), params,
                TransformInput.builder().metadata(Map.of("metadata-trigger-key", "Test warn message")).build());
        TransformResultAssert.assertThat(transformResultType).hasMessageSize(1)
                .hasWarning("Test warn message");
    }

    @Test
    void passesThroughOnMetadataNotPresent() {
        Warning action = new Warning();

        WarningParameters params = new WarningParameters();
        params.setMetadataTrigger("non-matching-key");
        DeltaFiTestRunner runner = DeltaFiTestRunner.setup("ErrorTest");
        ActionContent actionContent = ActionContent.emptyContent(runner.actionContext(), "empty", MediaType.TEXT_PLAIN);
        TransformResultType transformResultType = action.transform(runner.actionContext(), params,
                TransformInput.builder().metadata(Map.of("metadata-trigger-key", "Test error message"))
                        .content(List.of(actionContent)).build());
        TransformResultAssert.assertThat(transformResultType).hasMessageSize(0);
    }
}