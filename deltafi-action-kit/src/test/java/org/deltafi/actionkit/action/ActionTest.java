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
package org.deltafi.actionkit.action;

import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ActionTest {

    private static class A extends LoadAction<ActionParameters> {
        public A() {
            super("Actual nothing action at all");
        }

        @Override
        protected LoadInput buildInput(@NotNull ActionContext actionContext, @NotNull DeltaFileMessage deltaFileMessage) {
            return null;
        }

        @Override
        public LoadResult load(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull LoadInput input) {
            return null;
        }
    }

    private static class B<Z extends Number> extends A { }

    private static class C extends B<Integer> {}

    @InjectMocks
    C mock;

    @Test
    public void testCorrectGenericClassReturned() {
        Assertions.assertEquals(ActionParameters.class, mock.paramClass);
    }

}
