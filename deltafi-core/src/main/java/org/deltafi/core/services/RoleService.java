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

import com.fasterxml.uuid.Generators;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.repo.RoleRepo;
import org.deltafi.core.types.ErrorHolder;
import org.deltafi.core.types.Role;
import org.deltafi.core.types.Role.Input;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.deltafi.common.constant.DeltaFiConstants.ADMIN_ID;

@Slf4j
@Service
public class RoleService {

    private final RoleRepo roleRepo;
    private final PermissionsService permissionsService;
    @Getter
    private final boolean scheduledMaintenance;

    public RoleService(RoleRepo roleRepo, PermissionsService permissionsService, @Value("${schedule.maintenance:true}") boolean scheduledMaintenance) {
        this.roleRepo = roleRepo;
        this.permissionsService = permissionsService;
        this.scheduledMaintenance = scheduledMaintenance;
    }

    @PostConstruct
    public void init() {
        if (scheduledMaintenance && roleRepo.count() == 0) {
            OffsetDateTime now = OffsetDateTime.now();
            List<Role> defaultRoles = List.of(
                    Role.builder().id(ADMIN_ID).name("Admin").permission(DeltaFiConstants.ADMIN_PERMISSION).createdAt(now).updatedAt(now).build(),
                    Role.builder().id(Generators.timeBasedEpochGenerator().generate()).name("Ingress Only").permission("DeltaFileIngress").createdAt(now).updatedAt(now).build(),
                    Role.builder().id(Generators.timeBasedEpochGenerator().generate()).name("Read Only").permissions(
                            List.of("DashboardView", "DeletePolicyRead", "DeltaFileContentView", "DeltaFileMetadataView",
                                    "EventRead", "FlowView", "MetricsView", "PluginsView",
                                    "SnapshotRead", "StatusView", "SystemPropertiesRead", "UIAccess", "VersionsView"))
                            .createdAt(now).updatedAt(now).build());
            roleRepo.saveAll(defaultRoles);
        }
    }

    public List<Role> getRoles() {
        return roleRepo.findAll();
    }

    public Set<Role> getRoles(Set<UUID> ids) {
        List<Role> roles = roleRepo.findAllById(ids);
        if (roles.size() != ids.size()) {
            throw new InvalidRequestException("One or more role ids were not found");
        }

        return new HashSet<>(roles);
    }

    public Role getRole(UUID roleId) {
        return roleRepo.findById(roleId)
                .orElseThrow(() -> new EntityNotFound("Role with ID " + roleId + " not found."));
    }

    public Role createRole(Input roleInput) {
        Role role = new Role(roleInput);
        validateRole(role);
        return roleRepo.save(role);
    }

    public Role updateRole(UUID roleId, Input roleInput) {
        Role role = getRole(roleId);

        if (roleInput == null || roleInput.noChanges()) {
            return role;
        }

        roleInput.update(role);
        validateRole(role);
        return roleRepo.save(role);
    }

    public Role deleteRole(UUID roleId) {
        return roleRepo.deleteRoleAndAssociationsById(roleId)
                .orElseThrow(() -> new EntityNotFound("Role with ID " + roleId + " not found."));
    }

    public void deleteAllRoles() {
        roleRepo.deleteAll();
        roleRepo.flush();
    }

    public void saveAll(List<Role> roles) {
        roleRepo.saveAll(roles);
    }

    private void validateRole(Role role) {
        ErrorHolder errors = new ErrorHolder();

        if (StringUtils.isBlank(role.getName())) {
            errors.add("name", "cannot be empty");
        } else if (isDuplicateName(role)) {
            errors.add("name", "must be unique");
        }

        if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
            errors.add("permissions", "cannot be empty");
        } else if (!permissionsService.validatePermission(role.getPermissions())) {
            errors.add("permissions","must contain only valid permissions");
        }

        if (errors.notEmpty()) {
            throw new InvalidEntityException(errors);
        }
    }

    boolean isDuplicateName(Role role) {
        return role.getId() != null ?
                roleRepo.existsByIdNotAndName(role.getId(), role.getName()) :
                roleRepo.existsByName(role.getName());
    }
}
