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

import org.deltafi.core.domain.types.TimedDeletePolicy;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TimedDeleteTest {
    final String policyName = "policyName";
    final String flow = "the flow";
    @Mock
    DeltaFilesService deltaFilesService;

    @Test
    void testConstructorAfterCreate() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setAfterCreate("PT50M");
        policy.setFlow(flow);
        TimedDelete timedDelete = new TimedDelete(1000, deltaFilesService, policy);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterCreate().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterComplete());
        assertThat(timedDelete.getFlow()).isEqualTo(flow);
    }

    @Test
    void testConstructorAfterComplete() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setAfterComplete("PT50M");
        TimedDelete timedDelete = new TimedDelete(1000, deltaFilesService, policy);
        assertThat(timedDelete.getName()).isEqualTo(policyName);
        assertThat(timedDelete.getAfterComplete().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterCreate());
        assertNull(timedDelete.getFlow());
    }

    @Test
    void testConstructorThrowsWithNoParams() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        assertThrows(IllegalArgumentException.class, () -> new TimedDelete(1000, deltaFilesService, policy));
    }

    @Test
    void testConstructorThrowsWithTooManyParams() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setAfterComplete("PT50M");
        policy.setAfterCreate("PT50M");
        assertThrows(IllegalArgumentException.class, () -> new TimedDelete(1000, deltaFilesService, policy));
    }

    @Test
    void runsAfterCreate() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setAfterCreate("PT50M");
        TimedDelete timedDelete = new TimedDelete(1000, deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).delete(ArgumentMatchers.any(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(0L), ArgumentMatchers.isNull(), ArgumentMatchers.eq(policyName), ArgumentMatchers.eq(false), ArgumentMatchers.eq(1000));
    }

    @Test
    void runsAfterComplete() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setAfterComplete("PT50M");
        policy.setFlow(flow);
        TimedDelete timedDelete = new TimedDelete(1000, deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).delete(ArgumentMatchers.isNull(), ArgumentMatchers.any(), ArgumentMatchers.eq(0L), ArgumentMatchers.eq(flow), ArgumentMatchers.eq(policyName), ArgumentMatchers.eq(false), ArgumentMatchers.eq(1000));
    }

    @Test
    void runsWithMinByes() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(policyName);
        policy.setMinBytes(234L);

        TimedDelete timedDelete = new TimedDelete(1000, deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).delete(ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(234L), ArgumentMatchers.isNull(), ArgumentMatchers.eq(policyName), ArgumentMatchers.eq(false), ArgumentMatchers.eq(1000));
    }
}
