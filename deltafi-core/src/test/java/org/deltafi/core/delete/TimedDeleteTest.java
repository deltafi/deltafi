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
package org.deltafi.core.delete;

import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.TimedDeletePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TimedDeleteTest {

    static final UUID ID = UUID.randomUUID();
    static final String POLICY_NAME = "policyName";
    static final String FLOW_NAME = "flowName";

    @Mock
    DeltaFilesService deltaFilesService;

    @Test
    void testConstructorAfterCreate() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setAfterCreate("PT50M");
        policy.setFlow(FLOW_NAME);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policy);
        assertThat(timedDelete.getName()).isEqualTo(POLICY_NAME);
        assertThat(timedDelete.getAfterCreate().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterComplete());
        assertThat(timedDelete.getFlow()).isEqualTo(FLOW_NAME);
    }

    @Test
    void testConstructorAfterComplete() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setAfterComplete("PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policy);
        assertThat(timedDelete.getName()).isEqualTo(POLICY_NAME);
        assertThat(timedDelete.getAfterComplete().toMinutes()).isEqualTo(50);
        assertNull(timedDelete.getAfterCreate());
        assertNull(timedDelete.getFlow());
    }

    @Test
    void testConstructorThrowsWithNoParams() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        assertThrows(IllegalArgumentException.class, () -> new TimedDelete(deltaFilesService, policy));
    }

    @Test
    void testConstructorThrowsWithTooManyParams() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setAfterComplete("PT50M");
        policy.setAfterCreate("PT50M");
        assertThrows(IllegalArgumentException.class, () -> new TimedDelete(deltaFilesService, policy));
    }

    @Test
    void runsAfterCreate() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setAfterCreate("PT50M");
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).timedDelete(ArgumentMatchers.any(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(0L), ArgumentMatchers.isNull(), ArgumentMatchers.eq(POLICY_NAME), ArgumentMatchers.eq(false));
    }

    @Test
    void runsAfterComplete() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setAfterComplete("PT50M");
        policy.setFlow(FLOW_NAME);
        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).timedDelete(ArgumentMatchers.isNull(), ArgumentMatchers.any(), ArgumentMatchers.eq(0L), ArgumentMatchers.eq(FLOW_NAME), ArgumentMatchers.eq(POLICY_NAME), ArgumentMatchers.eq(false));
    }

    @Test
    void runsWithMinByes() {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(ID);
        policy.setName(POLICY_NAME);
        policy.setMinBytes(234L);

        TimedDelete timedDelete = new TimedDelete(deltaFilesService, policy);
        timedDelete.run();

        verify(deltaFilesService).timedDelete(ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(234L), ArgumentMatchers.isNull(), ArgumentMatchers.eq(POLICY_NAME), ArgumentMatchers.eq(false));
    }
}
