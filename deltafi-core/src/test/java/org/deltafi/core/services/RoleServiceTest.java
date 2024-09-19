/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.assertj.core.api.Assertions;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.repo.RoleRepo;
import org.deltafi.core.types.ErrorHolder;
import org.deltafi.core.types.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    private static final UUID ID = UUID.randomUUID();
    private final RoleService roleService;
    private final RoleRepo roleRepo;

    @Captor
    ArgumentCaptor<Role> roleCaptor;

    @Captor
    ArgumentCaptor<List<Role>> rolesCaptor;

    public RoleServiceTest(@Mock RoleRepo roleRepo) throws IOException {
        this.roleService = new RoleService(roleRepo, new PermissionsService(new ClassPathResource("permissions.csv")));
        this.roleRepo = roleRepo;
    }

    @Test
    void verifyValidDefaults() throws IOException {
        PermissionsService permissionsService =  new PermissionsService(new ClassPathResource("permissions.csv"));
        this.roleService.init();

        Mockito.verify(roleRepo).saveAll(rolesCaptor.capture());

        List<Role> defaultRoles = rolesCaptor.getValue();
        assertThat(defaultRoles).hasSize(3);

        // if this test fails the init method needs to be updated to remove an invalid role and a migration
        // is required to remove the permission from the roles table
        for (Role roles : rolesCaptor.getValue()) {
            assertThat(permissionsService.validatePermission(roles.getPermissions())).isTrue();
        }
    }

    @Test
    void getRoles() {
        Role role = Role.builder().id(ID).name("roleName").build();
        Mockito.when(roleRepo.findAllById(Set.of(ID))).thenReturn(List.of(role));
        assertThat(roleService.getRoles(Set.of(ID))).isEqualTo(Set.of(role));
    }

    @Test
    void getRole() {
        Role role = Role.builder().id(ID).name("roleName").build();
        Mockito.when(roleRepo.findById(ID)).thenReturn(Optional.of(role));
        assertThat(roleService.getRole(ID)).isEqualTo(role);
    }

    @Test
    void getRoleNotFound() {
        Mockito.when(roleRepo.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> roleService.getRole(ID))
                .isInstanceOf(EntityNotFound.class)
                .hasMessage("Role with ID " + ID + " not found.");
    }

    @Test
    void createRole() {
        Role.Input roleInput = new Role.Input("rolename", List.of("UIAccess"));
        roleService.createRole(roleInput);

        Mockito.verify(roleRepo).save(roleCaptor.capture());

        Role role = roleCaptor.getValue();
        assertThat(role.getName()).isEqualTo("rolename");
        assertThat(role.getPermissions()).containsExactly("UIAccess");
        assertThat(role.getCreatedAt()).isNotNull();
        assertThat(role.getCreatedAt()).isEqualTo(role.getUpdatedAt());
    }

    @Test
    void createInvalidRole() {
        Role.Input roleInput = new Role.Input(null, null);
        ErrorHolder expected = new ErrorHolder();
        expected.add("name", "cannot be empty");
        expected.add("permissions", "cannot be empty");
        assertThatThrownBy(() -> roleService.createRole(roleInput))
                .isInstanceOf(InvalidEntityException.class)
                .extracting(e -> ((InvalidEntityException) e).getErrorHolder())
                .isEqualTo(expected);
        Mockito.verifyNoInteractions(roleRepo);
    }

    @Test
    void updateRole() {
        List<String> newPermissions = List.of("UIAccess", "Admin");
        OffsetDateTime time = OffsetDateTime.now().minusMinutes(1);
        Role role = Role.builder().id(ID).createdAt(time).updatedAt(time).permissions(List.of("UIAccess", "PluginsView")).build();
        Role.Input roleInput = new Role.Input("changed", newPermissions);

        Mockito.when(roleRepo.findById(role.getId())).thenReturn(Optional.of(role));
        roleService.updateRole(role.getId(), roleInput);

        assertThat(role.getUpdatedAt()).isAfter(time);
        assertThat(role.getName()).isEqualTo("changed");
        assertThat(role.getPermissions()).isEqualTo(newPermissions);
    }

    @Test
    void updateRoleInvalidUpdate() {
        Role role = Role.builder().id(ID).permissions(List.of("UIAccess", "PluginsView")).build();
        Role.Input roleInput = new Role.Input("changed", List.of("Unknown"));

        Mockito.when(roleRepo.findById(role.getId())).thenReturn(Optional.of(role));

        ErrorHolder expected = new ErrorHolder();
        expected.add("permissions", "must contain only valid permissions");

        assertThatThrownBy(() -> roleService.updateRole(ID, roleInput))
                .isInstanceOf(InvalidEntityException.class)
                .extracting(e -> ((InvalidEntityException) e).getErrorHolder())
                .isEqualTo(expected);
        Mockito.verify(roleRepo, Mockito.times(0)).save(Mockito.any(Role.class));
    }

    @Test
    void updateNoChanges() {
        Role role = Role.builder().id(ID).permissions(List.of("UIAccess", "PluginsView")).build();
        Role.Input roleInput = new Role.Input(null, null);

        Mockito.when(roleRepo.findById(role.getId())).thenReturn(Optional.of(role));
        roleService.updateRole(role.getId(), roleInput);
        Mockito.verify(roleRepo, Mockito.times(0)).save(Mockito.any(Role.class));
    }

    @Test
    void deleteRole() {
        Role role = Role.builder().id(ID).name("roleName").build();
        Mockito.when(roleRepo.deleteRoleAndAssociationsById(ID)).thenReturn(Optional.of(role));
        Assertions.assertThat(roleService.deleteRole(ID)).isEqualTo(role);
        Mockito.verify(roleRepo).deleteRoleAndAssociationsById(ID);
    }

    @Test
    void deleteNotFound() {
        Mockito.when(roleRepo.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> roleService.deleteRole(ID))
                .isInstanceOf(EntityNotFound.class)
                .hasMessage("Role with ID "  + ID + " not found.");
    }

}