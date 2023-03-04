/**
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
package org.deltafi.core.services;

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.repo.RetryPolicyRepo;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryPolicyServiceTest {

    private static final String DEFAULT_ID = "1";
    private static final String ERROR = "error";
    private static final String FLOW = "flow";
    private static final String ACTION = "action";
    private static final String ACTION_TYPE = "actionType";
    private static final String NOT_FOUND = "notFound";
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    RetryPolicyRepo retryPolicyRepo;

    @InjectMocks
    RetryPolicyService retryPolicyService;

    @Test
    void testDeleteFound() {
        when(retryPolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDefault()));
        assertTrue(retryPolicyService.remove(DEFAULT_ID));
        verify(retryPolicyRepo, times(1)).deleteById(DEFAULT_ID);
    }

    @Test
    void testDeleteNotFound() {
        when(retryPolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        assertFalse(retryPolicyService.remove(NOT_FOUND));
        verify(retryPolicyRepo, times(0)).deleteById(NOT_FOUND);
    }

    @Test
    void testInvalidSave() {
        Result result = retryPolicyService.save(new RetryPolicy());
        assertFalse(result.isSuccess());
        assertEquals(3, result.getErrors().size());
        verify(retryPolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testSave() {
        Result result = retryPolicyService.save(getDefault());
        assertTrue(result.isSuccess());
        verify(retryPolicyRepo, times(1)).save(Mockito.any());
    }

    @Test
    void testInvalidUpdate() {
        when(retryPolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        Result result = retryPolicyService.update(getDefault());
        assertFalse(result.isSuccess());
        verify(retryPolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testUpdate() {
        when(retryPolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDefault()));
        Result result = retryPolicyService.update(getDefault());
        assertTrue(result.isSuccess());
        verify(retryPolicyRepo, times(1)).save(Mockito.any());
    }

    @Test
    void testFind() {
        when(retryPolicyRepo.findAll()).thenReturn(getTestList());
        retryPolicyService.refreshCache();
        Optional<RetryPolicy> policy = retryPolicyService.find(ERROR, "2" + FLOW, "2" + ACTION, ACTION_TYPE);
        assertFalse(policy.isEmpty());
    }

    @Test
    void testGetRetryDelay() {
        when(retryPolicyRepo.findAll()).thenReturn(getTestList());
        retryPolicyService.refreshCache();

        Optional<Integer> delay = retryPolicyService.getRetryDelay(
                getDeltaFile(MAX_ATTEMPTS - 1),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause(ERROR).build())
                        .build(),
                ACTION_TYPE);
        assertFalse(delay.isEmpty());
        assertEquals(100, delay.get());

        Optional<Integer> tooManyAttempts = retryPolicyService.getRetryDelay(
                getDeltaFile(MAX_ATTEMPTS),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause(ERROR).build())
                        .build(),
                ACTION_TYPE);
        assertTrue(tooManyAttempts.isEmpty());

        Optional<Integer> wrongError = retryPolicyService.getRetryDelay(
                getDeltaFile(1),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause("not-a-match").build())
                        .build(),
                ACTION_TYPE);
        assertTrue(wrongError.isEmpty());
    }

    @Test
    void testComputeDelay() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        assertEquals(100, retryPolicyService
                .computeDelay(backOff, 1));
        assertEquals(100, retryPolicyService
                .computeDelay(backOff, 2));

        backOff.setRandom(true);
        backOff.setMaxDelay(200);
        int randomDelay = retryPolicyService.computeDelay(backOff, 1);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
        randomDelay = retryPolicyService.computeDelay(backOff, 2);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
        randomDelay = retryPolicyService.computeDelay(backOff, 3);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
    }

    @Test
    void testComputeDelayWithMultiplier() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        backOff.setRandom(false);
        backOff.setMultiplier(2);

        assertEquals(200, retryPolicyService
                .computeDelay(backOff, 1));
        assertEquals(400, retryPolicyService
                .computeDelay(backOff, 2));
        assertEquals(600, retryPolicyService
                .computeDelay(backOff, 3));
    }

    @Test
    void testComputeDelayWithMultiplierAndMaxDelay() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        backOff.setRandom(false);
        backOff.setMultiplier(2);
        backOff.setMaxDelay(250);

        assertEquals(200, retryPolicyService
                .computeDelay(backOff, 1));
        assertEquals(250, retryPolicyService
                .computeDelay(backOff, 2));
        assertEquals(250, retryPolicyService
                .computeDelay(backOff, 3));
    }

    private RetryPolicy getDefault() {
        return getCustom("");
    }

    List<RetryPolicy> getTestList() {
        return List.of(
                getCustom("1"),
                getCustom("2"),
                getCustom("3"));
    }

    private RetryPolicy getCustom(String prefix) {
        RetryPolicy policy = buildPolicy(ERROR, prefix + FLOW, prefix + ACTION, ACTION_TYPE);
        policy.setBackOff(BackOff.newBuilder().delay(100).build());
        return policy;
    }

    private RetryPolicy buildPolicy(String error, String flow, String action, String actionType) {
        RetryPolicy policy = new RetryPolicy();
        policy.setId(DEFAULT_ID);
        policy.setMaxAttempts(MAX_ATTEMPTS);

        if (null != error) policy.setErrorSubstring(error);
        if (null != flow) policy.setFlow(flow);
        if (null != action) policy.setAction(action);
        if (null != actionType) policy.setActionType(actionType);

        return policy;
    }

    DeltaFile getDeltaFile(int attempt) {
        OffsetDateTime now = OffsetDateTime.now();
        Action action = Action.newBuilder()
                .name("1" + ACTION)
                .state(ActionState.QUEUED)
                .created(now)
                .modified(now)
                .attempt(attempt)
                .build();

        return DeltaFile.newBuilder()
                .did(UUID.randomUUID().toString())
                .requeueCount(0)
                .stage(DeltaFileStage.INGRESS)
                .actions(new ArrayList<>(List.of(action)))
                .sourceInfo(SourceInfo.builder()
                        .flow("1" + FLOW)
                        .filename("filename").build())
                .protocolStack(Collections.emptyList())
                .domains(Collections.emptyList())
                .enrichment(Collections.emptyList())
                .formattedData(Collections.emptyList())
                .created(now)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }
}
