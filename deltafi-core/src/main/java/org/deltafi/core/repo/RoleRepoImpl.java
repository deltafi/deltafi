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
package org.deltafi.core.repo;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.deltafi.core.types.Role;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@SuppressWarnings("unused")
public class RoleRepoImpl implements RoleRepoCustom {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public Optional<Role> deleteRoleAndAssociationsById(UUID id) {
        Role role = entityManager.find(Role.class, id);
        if (role != null) {
            entityManager.createNativeQuery("DELETE FROM user_roles WHERE role_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.remove(role);
        }
        return Optional.ofNullable(role);
    }
}

