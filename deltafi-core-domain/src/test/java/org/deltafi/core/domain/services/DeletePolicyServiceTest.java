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
package org.deltafi.core.domain.services;

import org.deltafi.core.domain.api.types.DeletePolicy;
import org.deltafi.core.domain.api.types.DiskSpaceDeletePolicy;
import org.deltafi.core.domain.generated.types.DiskSpaceDeletePolicyInput;
import org.deltafi.core.domain.generated.types.LoadDeletePoliciesInput;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.generated.types.TimedDeletePolicyInput;
import org.deltafi.core.domain.repo.DeletePolicyRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletePolicyServiceTest {

    private static final String LOCKED_DISABLED_POLICY = "lockedDisabled";
    private static final String LOCKED_ENABLED_POLICY = "lockedEnabled";
    private static final String UNLOCKED_DISABLED_POLICY = "unlockedDisabled";
    private static final String UNLOCKED_ENABLED_POLICY = "unlockedEnabled";
    private static final String NOT_FOUND = "notFound";

    private static final boolean REPLACE_ALL = true;
    private static final boolean DO_NOT_REPLACE = false;

    @Mock
    DeletePolicyRepo deletePolicyRepo;

    @InjectMocks
    DeletePolicyService deletePolicyService;

    @Test
    void testEnableUnlockedSuccess() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getUnlockedDisabled()));
        assertTrue(deletePolicyService.enablePolicy(UNLOCKED_DISABLED_POLICY, true));

        DeletePolicy changed = getUnlockedDisabled();
        changed.setEnabled(true);
        verify(deletePolicyRepo, times(1)).save(changed);
    }

    @Test
    void testUnlockedAlreadyEnabled() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getUnlockedEnabled()));
        assertFalse(deletePolicyService.enablePolicy(UNLOCKED_ENABLED_POLICY, true));
        verify(deletePolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testCannotDisableLocked() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getLockedEnabled()));
        assertFalse(deletePolicyService.enablePolicy(LOCKED_ENABLED_POLICY, false));
        verify(deletePolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testEnableLocked() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getLockedDisabled()));
        assertTrue(deletePolicyService.enablePolicy(LOCKED_DISABLED_POLICY, true));

        DeletePolicy changed = getLockedDisabled();
        changed.setEnabled(true);
        verify(deletePolicyRepo, times(1)).save(changed);
    }

    @Test
    void testDeleteFound() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getUnlockedDisabled()));
        assertTrue(deletePolicyService.remove(UNLOCKED_DISABLED_POLICY));
        verify(deletePolicyRepo, times(1)).deleteById(UNLOCKED_DISABLED_POLICY);
    }

    @Test
    void testDeleteNotFound() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        assertFalse(deletePolicyService.remove(NOT_FOUND));
        verify(deletePolicyRepo, times(0)).deleteById(NOT_FOUND);
    }

    @Test
    void testSaveAll() {
        assertTrue(deletePolicyService.saveAll(DO_NOT_REPLACE, getValidSave()).getSuccess());
        verify(deletePolicyRepo, times(0)).deleteAll();
        verify(deletePolicyRepo, times(1)).saveAll(Mockito.any());
    }

    @Test
    void testReplaceAll() {
        assertTrue(deletePolicyService.saveAll(REPLACE_ALL, getValidSave()).getSuccess());
        verify(deletePolicyRepo, times(1)).deleteAll();
        verify(deletePolicyRepo, times(1)).saveAll(Mockito.any());
    }

    @Test
    void testInvalidSave() {
        Result result = deletePolicyService.saveAll(REPLACE_ALL, getInvalidSave());
        assertFalse(result.getSuccess());
        verify(deletePolicyRepo, times(0)).deleteAll();
        verify(deletePolicyRepo, times(0)).saveAll(Mockito.any());
    }

    private List<DeletePolicy> getAllPolicies() {
        return List.of(
                getLockedDisabled(),
                getLockedEnabled(),
                getUnlockedDisabled(),
                getUnlockedEnabled());
    }

    private DeletePolicy getLockedDisabled() {
        return buildPolicy(LOCKED_ENABLED_POLICY, true, false);
    }

    private DeletePolicy getLockedEnabled() {
        return buildPolicy(LOCKED_ENABLED_POLICY, true, true);
    }

    private DeletePolicy getUnlockedDisabled() {
        return buildPolicy(LOCKED_ENABLED_POLICY, false, false);
    }

    private DeletePolicy getUnlockedEnabled() {
        return buildPolicy(LOCKED_ENABLED_POLICY, false, true);
    }

    private DeletePolicy buildPolicy(String name, boolean locked, boolean enabled) {
        DiskSpaceDeletePolicy policy = new DiskSpaceDeletePolicy();
        policy.setId(name);
        policy.setLocked(locked);
        policy.setEnabled(enabled);
        policy.setMaxPercent(90);
        return policy;
    }

    private LoadDeletePoliciesInput getValidSave() {
        LoadDeletePoliciesInput input = LoadDeletePoliciesInput.newBuilder()
                .diskSpacePolicies(List.of(
                        DiskSpaceDeletePolicyInput.newBuilder()
                                .id("disk1")
                                .maxPercent(90)
                                .build()))
                .timedPolicies(List.of(
                        TimedDeletePolicyInput.newBuilder()
                                .id("timed1")
                                .afterComplete("PT50M")
                                .build())).build();
        return input;
    }

    private LoadDeletePoliciesInput getInvalidSave() {
        LoadDeletePoliciesInput input = LoadDeletePoliciesInput.newBuilder()
                .diskSpacePolicies(List.of(
                        DiskSpaceDeletePolicyInput.newBuilder()
                                .id("disk1")
                                .maxPercent(-1)
                                .build()))
                .timedPolicies(List.of(
                        TimedDeletePolicyInput.newBuilder()
                                .id("timed1")
                                .afterComplete("PT50M")
                                .build())).build();
        return input;
    }

}
