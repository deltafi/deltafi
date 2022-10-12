/*
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

package org.deltafi.core.action;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SourceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

@Component
@Slf4j
@ExtendWith(MockitoExtension.class)
class FilterByFiatTransformActionTest {

    private static final String DID = UUID.randomUUID().toString();
    private static final String FLOW = "theFlow";
    private static final String ACTION_VERSION = "0.0";
    private static final String FILE_NAME = "MyFileName";

    ActionContext ACTION_CONTEXT = ActionContext.builder()
            .actionVersion(ACTION_VERSION)
            .did(DID)
            .name("MyFilterByFiatTransformAction")
            .ingressFlow(FLOW)
            .build();

    @InjectMocks
    FilterByFiatTransformAction action;

    @Test
    @SneakyThrows
    void transformTest() {
        Result result = action.transform(ACTION_CONTEXT, sourceInfo(), new Content(), Collections.emptyMap());
        assertThat(result, instanceOf(FilterResult.class));

    }

    SourceInfo sourceInfo() {
        return new SourceInfo(FILE_NAME, FLOW, List.of());
    }


}