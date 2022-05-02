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
package org.deltafi.core.domain.delete;

import org.deltafi.core.domain.services.DeltaFilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TimedDeleteTest {
    @Mock
    DeltaFilesService deltaFilesService;

    final Map<String, String> parameters = new HashMap<>();
    final String policyName = "policyName";
    final String flow = "the flow";

    @BeforeEach
    void setup() {
        parameters.clear();
    }

    @Test
    void testConstructorAfterCreate() {
        parameters.put("afterCreate", "PT50M");
        parameters.put("flow", flow);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterCreate().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterComplete());
        assertThat(timedDelete.getFlow()).isEqualTo(flow);
    }

    @Test
    void testConstructorAfterComplete() {
        parameters.put("afterComplete", "PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterComplete().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterCreate());
        assertNull(timedDelete.getFlow());
    }

    @Test
    void testConstructorThrowsWithNoParams() {
        assertThrows(IllegalArgumentException.class,() -> new TimedDelete(deltaFilesService, policyName, parameters));
    }

    @Test
    void testConstructorThrowsWithTooManyParams() {
        parameters.put("afterCreate", "PT50M");
        parameters.put("afterComplete", "PT50M");
        assertThrows(IllegalArgumentException.class,() -> new TimedDelete(deltaFilesService, policyName, parameters));
    }

    @Test
    void runsAfterCreate() {
        parameters.put("afterCreate", "PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        timedDelete.run();

        verify(deltaFilesService).markForDelete(ArgumentMatchers.any(), ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(policyName));
    }

    @Test
    void runsAfterComplete() {
        parameters.put("afterComplete", "PT50M");
        parameters.put("flow", flow);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policyName, parameters);
        timedDelete.run();

        verify(deltaFilesService).markForDelete(ArgumentMatchers.isNull(), ArgumentMatchers.any(), ArgumentMatchers.eq(flow), ArgumentMatchers.eq(policyName));
    }
}