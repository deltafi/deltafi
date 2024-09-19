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
package org.deltafi.core.types;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record DeltaFiUserDTO(UUID id, String name, String dn, String username, OffsetDateTime createdAt,
                             OffsetDateTime updatedAt, Set<Role> roles, Set<String> permissions) {
    public DeltaFiUserDTO(DeltaFiUser user) {
        this(user.getId(), user.getName(), user.getDn(), user.getUsername(), user.getCreatedAt(), user.getUpdatedAt(), user.getRoles(), permissions(user.getRoles()));
    }

    private static Set<String> permissions(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }

        return roles.stream()
                .map(Role::getPermissions)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
