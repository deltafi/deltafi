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
package org.deltafi.core.rest;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.RoleService;
import org.deltafi.core.types.Role;
import org.deltafi.core.util.UpdateMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.deltafi.common.constant.DeltaFiConstants.ADMIN_ID;

@RestController
@RequestMapping("/api/v2/roles")
@RequiredArgsConstructor
public class RolesRest {
    private final RoleService roleService;
    private final CoreAuditLogger auditLogger;

    @GetMapping
    @NeedsPermission.RoleRead
    public List<Role> getRoles() {
        return roleService.getRoles();
    }

    @GetMapping("/{id}")
    @NeedsPermission.RoleRead
    public Role getRole(@PathVariable("id") UUID roleId) {
        return roleService.getRole(roleId);
    }

    @PostMapping
    @NeedsPermission.RoleCreate
    public Role createRole(@RequestBody Role.Input role) {
        Role createdRole = roleService.createRole(role);
        auditLogger.audit("created role {}", createdRole.getName());
        return createdRole;
    }

    @PutMapping("/{id}")
    @NeedsPermission.RoleUpdate
    public Role updateRole(@PathVariable("id") UUID roleId, @RequestBody String updates) {
        Role.Input roleInput = UpdateMapper.readValue(updates, Role.Input.class);
        Role updated = roleService.updateRole(roleId, roleInput);
        auditLogger.audit("updated role {}", updated.getName());
        return updated;
    }

    @DeleteMapping("/{id}")
    @NeedsPermission.RoleDelete
    public Role deleteRole(@PathVariable("id") UUID roleId) {
        if (ADMIN_ID.equals(roleId)) {
            throw new InvalidRequestException(HttpStatus.FORBIDDEN, "Unable to delete the admin role.");
        }

        Role destroyed = roleService.deleteRole(roleId);
        auditLogger.audit("deleted role {}", destroyed.getName());
        return destroyed;
    }
}
