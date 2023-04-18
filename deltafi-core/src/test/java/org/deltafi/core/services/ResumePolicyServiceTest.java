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
import org.deltafi.core.repo.ResumePolicyRepo;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.ResumePolicy;
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
class ResumePolicyServiceTest {

    private static final String DEFAULT_ID = "1";
    private static final String ERROR = "error";
    private static final String FLOW = "flow";
    private static final String ACTION = "action";
    private static final String ACTION_TYPE = "actionType";
    private static final String NOT_FOUND = "notFound";
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    ResumePolicyRepo resumePolicyRepo;

    @InjectMocks
    ResumePolicyService resumePolicyService;

    @Test
    void testDeleteFound() {
        when(resumePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDefault()));
        assertTrue(resumePolicyService.remove(DEFAULT_ID));
        verify(resumePolicyRepo, times(1)).deleteById(DEFAULT_ID);
    }

    @Test
    void testDeleteNotFound() {
        when(resumePolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        assertFalse(resumePolicyService.remove(NOT_FOUND));
        verify(resumePolicyRepo, times(0)).deleteById(NOT_FOUND);
    }

    @Test
    void testInvalidSave() {
        Result result = resumePolicyService.save(new ResumePolicy());
        assertFalse(result.isSuccess());
        assertEquals(4, result.getErrors().size());
        verify(resumePolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testSave() {
        Result result = resumePolicyService.save(getDefault());
        assertTrue(result.isSuccess());
        verify(resumePolicyRepo, times(1)).save(Mockito.any());
    }

    @Test
    void testInvalidUpdate() {
        when(resumePolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        Result result = resumePolicyService.update(getDefault());
        assertFalse(result.isSuccess());
        verify(resumePolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testUpdate() {
        when(resumePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDefault()));
        Result result = resumePolicyService.update(getDefault());
        assertTrue(result.isSuccess());
        verify(resumePolicyRepo, times(1)).save(Mockito.any());
    }

    @Test
    void testFind() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();
        Optional<ResumePolicy> policy = resumePolicyService.find(1, ERROR, "2" + FLOW, "2" + ACTION, ACTION_TYPE);
        assertFalse(policy.isEmpty());
        assertEquals("name2", policy.get().getName());
    }

    @Test
    void testGetAutoResumeDelay() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();

        Optional<ResumePolicyService.ResumeDetails> resumeDetails = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS - 1),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause(ERROR).build())
                        .build(),
                ACTION_TYPE);
        assertFalse(resumeDetails.isEmpty());
        assertEquals(100, resumeDetails.get().delay());
        assertEquals("name1", resumeDetails.get().name());

        Optional<ResumePolicyService.ResumeDetails> rollToNextPolicy = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS + 1),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause(ERROR).build())
                        .build(),
                ACTION_TYPE);
        assertFalse(rollToNextPolicy.isEmpty());
        assertEquals(100, rollToNextPolicy.get().delay());
        assertEquals("name4", rollToNextPolicy.get().name());

        Optional<ResumePolicyService.ResumeDetails> tooManyAttempts = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS * 2),
                ActionEventInput.newBuilder()
                        .action("1" + ACTION)
                        .error(ErrorEvent.newBuilder()
                                .cause(ERROR).build())
                        .build(),
                ACTION_TYPE);
        assertTrue(tooManyAttempts.isEmpty());

        Optional<ResumePolicyService.ResumeDetails> wrongError = resumePolicyService.getAutoResumeDelay(
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
        assertEquals(100, resumePolicyService
                .computeDelay(backOff, 1));
        assertEquals(100, resumePolicyService
                .computeDelay(backOff, 2));

        backOff.setRandom(true);
        backOff.setMaxDelay(200);
        int randomDelay = resumePolicyService.computeDelay(backOff, 1);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
        randomDelay = resumePolicyService.computeDelay(backOff, 2);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
        randomDelay = resumePolicyService.computeDelay(backOff, 3);
        assertTrue(randomDelay >= 100 && randomDelay <= 200);
    }

    @Test
    void testComputeDelayWithMultiplier() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        backOff.setRandom(false);
        backOff.setMultiplier(2);

        assertEquals(200, resumePolicyService
                .computeDelay(backOff, 1));
        assertEquals(400, resumePolicyService
                .computeDelay(backOff, 2));
        assertEquals(600, resumePolicyService
                .computeDelay(backOff, 3));
    }

    @Test
    void testComputeDelayWithMultiplierAndMaxDelay() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        backOff.setRandom(false);
        backOff.setMultiplier(2);
        backOff.setMaxDelay(250);

        assertEquals(200, resumePolicyService
                .computeDelay(backOff, 1));
        assertEquals(250, resumePolicyService
                .computeDelay(backOff, 2));
        assertEquals(250, resumePolicyService
                .computeDelay(backOff, 3));
    }

    private ResumePolicy getDefault() {
        return getCustom("name", "");
    }

    private List<ResumePolicy> getTestList() {
        return List.of(
                getCustom("name1", "1"),
                getCustom("name2", "2"),
                getCustom("name3", "3"),
                getCustom("name4", "1", MAX_ATTEMPTS * 2));
    }

    private ResumePolicy getCustom(String name, String prefix) {
        return getCustom(name, prefix, MAX_ATTEMPTS);
    }

    private ResumePolicy getCustom(String name, String prefix, int maxAttempts) {
        ResumePolicy policy = buildPolicy(name, ERROR, prefix + FLOW, prefix + ACTION, ACTION_TYPE, maxAttempts);
        policy.setBackOff(BackOff.newBuilder().delay(100).build());
        return policy;
    }

    private ResumePolicy buildPolicy(String name, String error, String flow, String action, String actionType, int maxAttempts) {
        ResumePolicy policy = new ResumePolicy();
        policy.setId(DEFAULT_ID);
        policy.setName(name);
        policy.setMaxAttempts(maxAttempts);
        policy.setMaxAttempts(maxAttempts);

        if (null != error) policy.setErrorSubstring(error);
        if (null != flow) policy.setFlow(flow);
        if (null != action) policy.setAction(action);
        if (null != actionType) policy.setActionType(actionType);

        return policy;
    }

    private DeltaFile getDeltaFile(int attempt) {
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
