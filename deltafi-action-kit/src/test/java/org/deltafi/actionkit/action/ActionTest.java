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
package org.deltafi.actionkit.action;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ActionTest {
    @Data
    @EqualsAndHashCode(callSuper = true)
    private static class TestActionParameters extends ActionParameters {
        private String testParameter;
    }

    private static class A extends TransformAction<TestActionParameters> {
        public A() {
            super("Actual nothing action at all");
        }

        @Override
        protected TransformInput buildInput(@NotNull ActionContext actionContext, @NotNull DeltaFileMessage deltaFileMessage) {
            return null;
        }

        @Override
        public TransformResult transform(@NotNull ActionContext context, @NotNull TestActionParameters params, @NotNull TransformInput input) {
            return null;
        }
    }

    private static class B<Z extends Number> extends A { }

    private static class C extends B<Integer> {}

    C action = new C();

    @Test
    public void initializes() {
        assertEquals("org.deltafi.actionkit.action.ActionTest.C", action.getClassCanonicalName());
        assertEquals(ActionType.TRANSFORM, action.getActionType());
        assertEquals("Actual nothing action at all", action.getActionOptions().getDescription());
        assertEquals(TestActionParameters.class, action.getParamClass());
    }
}
