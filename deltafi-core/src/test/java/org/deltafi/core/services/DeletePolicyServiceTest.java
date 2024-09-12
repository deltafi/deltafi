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

import org.deltafi.core.repo.DeletePolicyRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletePolicyServiceTest {

    private static final UUID DISABLED_POLICY = UUID.randomUUID();
    private static final UUID ENABLED_POLICY = UUID.randomUUID();
    private static final UUID NOT_FOUND = UUID.randomUUID();

    private static final boolean REPLACE_ALL = true;
    private static final boolean DO_NOT_REPLACE = false;

    @Mock
    DeletePolicyRepo deletePolicyRepo;

    @InjectMocks
    DeletePolicyService deletePolicyService;

    @Test
    void testEnableSuccess() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDisabled()));
        assertTrue(deletePolicyService.enablePolicy(DISABLED_POLICY, true));

        DeletePolicy changed = getDisabled();
        changed.setEnabled(true);
        verify(deletePolicyRepo, times(1)).save(changed);
    }

    @Test
    void testAlreadyEnabled() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getEnabled()));
        assertFalse(deletePolicyService.enablePolicy(ENABLED_POLICY, true));
        verify(deletePolicyRepo, times(0)).save(Mockito.any());
    }

    @Test
    void testDeleteFound() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.of(getDisabled()));
        assertTrue(deletePolicyService.remove(DISABLED_POLICY));
        verify(deletePolicyRepo, times(1)).deleteById(DISABLED_POLICY);
    }

    @Test
    void testDeleteNotFound() {
        when(deletePolicyRepo.findById(Mockito.any())).thenReturn(Optional.empty());
        assertFalse(deletePolicyService.remove(NOT_FOUND));
        verify(deletePolicyRepo, times(0)).deleteById(NOT_FOUND);
    }

    @Test
    void testSaveAll() {
        assertTrue(deletePolicyService.saveAll(DO_NOT_REPLACE, getValidSave()).isSuccess());
        verify(deletePolicyRepo, times(0)).deleteAll();
        verify(deletePolicyRepo, times(1)).saveAll(Mockito.any());
    }

    @Test
    void testReplaceAll() {
        assertTrue(deletePolicyService.saveAll(REPLACE_ALL, getValidSave()).isSuccess());
        verify(deletePolicyRepo, times(1)).deleteAll();
        verify(deletePolicyRepo, times(1)).saveAll(Mockito.any());
    }

    @Test
    void testInvalidSave() {
        Result result = deletePolicyService.saveAll(REPLACE_ALL, getInvalidSave());
        assertFalse(result.isSuccess());
        verify(deletePolicyRepo, times(0)).deleteAll();
        verify(deletePolicyRepo, times(0)).saveAll(Mockito.any());
    }

    @Test
    void testUpdateSnapshot() {
        DiskSpaceDeletePolicy diskSpaceDeletePolicy = buildDiskSpaceDeletePolicy(50);
        TimedDeletePolicy timedDeletePolicy = buildTimeDeletePolicy();
        SystemSnapshot snapshot = new SystemSnapshot();

        Mockito.when(deletePolicyRepo.findAll()).thenReturn(List.of(diskSpaceDeletePolicy, timedDeletePolicy));
        deletePolicyService.updateSnapshot(snapshot);

        assertThat(snapshot.getDeletePolicies().getDiskSpacePolicies()).hasSize(1).contains(diskSpaceDeletePolicy);
        assertThat(snapshot.getDeletePolicies().getTimedPolicies()).hasSize(1).contains(timedDeletePolicy);
    }

    @Test
    void testResetFromSnapshot() {
        DeletePolicies deletePolicies = getValidSave();
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setDeletePolicies(deletePolicies);

        Result result = deletePolicyService.resetFromSnapshot(systemSnapshot, true);

        Mockito.verify(deletePolicyRepo).deleteAll();
        Mockito.verify(deletePolicyRepo).saveAll(deletePolicies.allPolicies());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    private DeletePolicy getEnabled() {
        return buildPolicy(true);
    }

    private DeletePolicy getDisabled() {
        return buildPolicy(false);
    }

    private DeletePolicy buildPolicy(boolean enabled) {
        DiskSpaceDeletePolicy policy = new DiskSpaceDeletePolicy();
        policy.setId(DeletePolicyServiceTest.ENABLED_POLICY);
        policy.setEnabled(enabled);
        policy.setMaxPercent(90);
        return policy;
    }

    private DeletePolicies getValidSave() {
        return DeletePolicies.builder()
                .diskSpacePolicies(List.of(buildDiskSpaceDeletePolicy(90)))
                .timedPolicies(List.of(buildTimeDeletePolicy())).build();
    }

    private DeletePolicies getInvalidSave() {
        return DeletePolicies.builder()
                .diskSpacePolicies(List.of(buildDiskSpaceDeletePolicy(-1)))
                .timedPolicies(List.of(buildTimeDeletePolicy())).build();
    }

    DiskSpaceDeletePolicy buildDiskSpaceDeletePolicy(int maxPercent) {
        return DiskSpaceDeletePolicy.builder()
                .id(UUID.randomUUID())
                .name("disk1")
                .maxPercent(maxPercent)
                .build();
    }

    TimedDeletePolicy buildTimeDeletePolicy() {
        return TimedDeletePolicy.builder()
                .id(UUID.randomUUID())
                .name("timed1")
                .afterComplete("PT50M")
                .build();
    }
}
