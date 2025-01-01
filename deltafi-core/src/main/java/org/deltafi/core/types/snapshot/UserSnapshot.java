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
import org.deltafi.core.types.DeltaFiUser;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Holds a copy of a DeltaFi user. Note - this is used instead of using DeltaFiUser directly
 * in snapshots due to the annotations in DeltaFiUser and Role causing errors
 * when storing a new snapshot
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshot {

    private UUID id;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String username;
    private String password;
    private String dn;
    private Set<RoleSnapshot> roles = new HashSet<>();

    public UserSnapshot(DeltaFiUser user) {
        this.id = user.getId();
        this.name = user.getName();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.dn = user.getDn();
        this.roles = user.getRoles() != null ? user.getRoles().stream().map(RoleSnapshot::new).collect(Collectors.toSet()) : Set.of();
    }

    public DeltaFiUser toDeltaFiUser() {
        DeltaFiUser user = new DeltaFiUser();
        user.setId(id);
        user.setName(name);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);
        user.setUsername(username);
        user.setPassword(password);
        user.setDn(dn);
        user.setRoles(roles.stream().map(RoleSnapshot::toRole).collect(Collectors.toSet()));
        return user;
    }
}
