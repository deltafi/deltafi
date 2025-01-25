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
package org.deltafi.core.services;

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.repo.ResumePolicyRepo;
import org.deltafi.core.types.*;
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

    private static final UUID DEFAULT_ID = UUID.randomUUID();
    private static final String ERROR = "error";
    private static final String DATA_SOURCE = "dataSource";
    private static final String ACTION = "action";
    private static final UUID NOT_FOUND = UUID.randomUUID();
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
        Optional<ResumePolicy> policy = resumePolicyService.find(1, ERROR, "2" + DATA_SOURCE, "2" + ACTION);
        assertFalse(policy.isEmpty());
        assertEquals("name2", policy.get().getName());
    }

    @Test
    void testGetAutoResumeDelay() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();

        Action errored = Action.builder().name("1action").type(ActionType.TRANSFORM).attempt(MAX_ATTEMPTS - 1).build();

        Optional<ResumePolicyService.ResumeDetails> resumeDetails = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS - 1), errored, ERROR);
        assertFalse(resumeDetails.isEmpty());
        assertEquals(100, resumeDetails.get().delay());
        assertEquals("name1", resumeDetails.get().name());

        errored.setAttempt(MAX_ATTEMPTS + 1); // too many attempts for policy name1, roll to name4
        Optional<ResumePolicyService.ResumeDetails> rollToNextPolicy = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS + 1), errored, ERROR);

        assertFalse(rollToNextPolicy.isEmpty());
        assertEquals(100, rollToNextPolicy.get().delay());
        assertEquals("name4", rollToNextPolicy.get().name());

        // errorCause does not match any policies
        Optional<ResumePolicyService.ResumeDetails> wrongError = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(1), errored, "not-a-match");
        assertTrue(wrongError.isEmpty());

        errored.setAttempt(MAX_ATTEMPTS * 2);
        Optional<ResumePolicyService.ResumeDetails> tooManyAttempts = resumePolicyService.getAutoResumeDelay(
                getDeltaFile(MAX_ATTEMPTS * 2), errored, ERROR);
        assertTrue(tooManyAttempts.isEmpty());
    }

    @Test
    void testComputeDelay() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        assertEquals(100, resumePolicyService.computeDelay(backOff, 1));
        assertEquals(100, resumePolicyService.computeDelay(backOff, 2));

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

        assertEquals(200, resumePolicyService.computeDelay(backOff, 1));
        assertEquals(400, resumePolicyService.computeDelay(backOff, 2));
        assertEquals(600, resumePolicyService.computeDelay(backOff, 3));
    }

    @Test
    void testComputeDelayWithMultiplierAndMaxDelay() {
        BackOff backOff = new BackOff();
        backOff.setDelay(100);
        backOff.setRandom(false);
        backOff.setMultiplier(2);
        backOff.setMaxDelay(250);

        assertEquals(200, resumePolicyService.computeDelay(backOff, 1));
        assertEquals(250, resumePolicyService.computeDelay(backOff, 2));
        assertEquals(250, resumePolicyService.computeDelay(backOff, 3));
    }

    @Test
    void testFindByName() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();

        Optional<ResumePolicy> found = resumePolicyService.findByName("name2");
        assertTrue(found.isPresent());
        assertEquals("2" + DATA_SOURCE, found.get().getDataSource());

        Optional<ResumePolicy> notFound = resumePolicyService.findByName("name999");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void testMatchesActionError() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();

        // DeltaFile matches policy: name1
        assertTrue(resumePolicyService.findByName("name1").isPresent());
        assertTrue(resumePolicyService.matchesActionError(
                resumePolicyService.findByName("name1").get(),
                getDeltaFile("1", true)));

        // no error action
        assertFalse(resumePolicyService.matchesActionError(
                resumePolicyService.findByName("name1").get(),
                getDeltaFile("1", false)));

        // DeltaFile/action doesn't match policy values
        assertFalse(resumePolicyService.matchesActionError(
                resumePolicyService.findByName("name1").get(),
                getDeltaFile("2", true)));

        assertTrue(resumePolicyService.findByName("name2").isPresent());
        // DeltaFile/action doesn't match policy values (reversed)
        assertFalse(resumePolicyService.matchesActionError(
                resumePolicyService.findByName("name2").get(),
                getDeltaFile("1", true)));

        // DeltaFile matches policy: name1
        assertTrue(resumePolicyService.matchesActionError(
                resumePolicyService.findByName("name2").get(),
                getDeltaFile("2", true)));
    }

    @Test
    void testCanBeApplied() {
        when(resumePolicyRepo.findByOrderByPriorityDesc()).thenReturn(getTestList());
        resumePolicyService.refreshCache();

        List<DeltaFile> deltaFiles = List.of(
                getDeltaFile("1", true),
                getDeltaFile("2", true),
                getDeltaFile("1", false),
                getDeltaFile("1", true),
                getDeltaFile("1", true));
        Set<UUID> excludes = Set.of(deltaFiles.get(1).getDid(), deltaFiles.getLast().getDid(), UUID.randomUUID());

        assertTrue(resumePolicyService.findByName("name1").isPresent());
        List<UUID> dids = resumePolicyService.canBeApplied(
                resumePolicyService.findByName("name1").get(), deltaFiles, excludes).stream()
                        .map(DeltaFile::getDid).toList();
        assertEquals(List.of(deltaFiles.getFirst().getDid(), deltaFiles.get(3).getDid()), dids);

        assertTrue(resumePolicyService.findByName("name3").isPresent());
        List<UUID> noMatchese = resumePolicyService.canBeApplied(
                resumePolicyService.findByName("name3").get(), deltaFiles, excludes).stream()
                .map(DeltaFile::getDid).toList();
        assertTrue(noMatchese.isEmpty());
    }

    private DeltaFile getDeltaFile(String prefix, boolean withErrorAction) {
        Action action1 = Action.builder()
                .name(prefix + ACTION)
                .number(0)
                .type(ActionType.TRANSFORM)
                .state(ActionState.COMPLETE)
                .attempt(1)
                .build();

        Action action2;
        if (withErrorAction) {
            action2 = Action.builder()
                    .name(prefix + ACTION)
                    .number(1)
                    .type(ActionType.TRANSFORM)
                    .state(ActionState.ERROR)
                    .attempt(1)
                    .errorCause(ERROR)
                    .errorContext("context")
                    .build();
        } else {
            action2 = Action.builder()
                    .name(prefix + ACTION)
                    .number(1)
                    .type(ActionType.TRANSFORM)
                    .state(ActionState.COMPLETE)
                    .attempt(1)
                    .build();
        }

        String dataSource = prefix + DATA_SOURCE;
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .state(withErrorAction ? DeltaFileFlowState.ERROR : DeltaFileFlowState.COMPLETE)
                .flowDefinition(FlowDefinition.builder().name(dataSource).type(FlowType.REST_DATA_SOURCE).build())
                .actions(new ArrayList<>(List.of(action1, action2))).build();
        return DeltaFile.builder()
                .did(UUID.randomUUID())
                .dataSource(dataSource)
                .flows(Set.of(flow))
                .build();
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
        ResumePolicy policy = buildPolicy(name, ERROR, prefix + DATA_SOURCE, prefix + ACTION, ActionType.TRANSFORM, maxAttempts);
        policy.setBackOff(BackOff.newBuilder().delay(100).build());
        return policy;
    }

    @SuppressWarnings("SameParameterValue")
    private ResumePolicy buildPolicy(String name, String error, String dataSource, String action, ActionType actionType, int maxAttempts) {
        ResumePolicy policy = new ResumePolicy();
        policy.setId(DEFAULT_ID);
        policy.setName(name);
        policy.setMaxAttempts(maxAttempts);
        policy.setMaxAttempts(maxAttempts);

        if (null != error) policy.setErrorSubstring(error);
        if (null != dataSource) policy.setDataSource(dataSource);
        if (null != action) policy.setAction(action);

        return policy;
    }

    private DeltaFile getDeltaFile(int attempt) {
        OffsetDateTime now = OffsetDateTime.now();

        Action action = Action.builder()
                .name("1" + ACTION)
                .state(ActionState.QUEUED)
                .type(ActionType.TRANSFORM)
                .created(now)
                .modified(now)
                .attempt(attempt)
                .build();

        String dataSource = 1 + DATA_SOURCE;
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .flowDefinition(FlowDefinition.builder().name(dataSource).type(FlowType.REST_DATA_SOURCE).build())
                .actions(new ArrayList<>(List.of(action))).build();

        return DeltaFile.builder()
                .did(UUID.randomUUID())
                .dataSource(dataSource)
                .requeueCount(0)
                .stage(DeltaFileStage.IN_FLIGHT)
                .flows(new LinkedHashSet<>(List.of(flow)))
                .name("filename")
                .created(now)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }
}
