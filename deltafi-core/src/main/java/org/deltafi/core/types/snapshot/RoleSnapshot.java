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
package org.deltafi.core.types.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.types.Role;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Holds a copy of a role. Note - this is used instead of using Role directly
 * in snapshots due to the annotations in DeltaFiUser and Role causing errors
 * when storing a new snapshot
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleSnapshot {
    private UUID id;
    private String name;
    private List<String> permissions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RoleSnapshot(Role role) {
        this.id = role.getId();
        this.name = role.getName();
        this.permissions = role.getPermissions();
        this.createdAt = role.getCreatedAt();
        this.updatedAt = role.getUpdatedAt();
    }

    public Role toRole() {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setPermissions(permissions);
        role.setCreatedAt(createdAt);
        role.setUpdatedAt(updatedAt);
        return role;
    }
}
