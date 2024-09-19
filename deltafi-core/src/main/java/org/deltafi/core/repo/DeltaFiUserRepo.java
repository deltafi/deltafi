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

import org.deltafi.core.types.DeltaFiUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeltaFiUserRepo extends JpaRepository<DeltaFiUser, UUID> {

    /**
     * Upsert the admin user. Sets the initial
     * values for the admin user on insert, otherwise
     * the user is unchanged if it already exists
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value =
            """
            INSERT INTO users (id, name, created_at, updated_at, username, password, dn)
            VALUES (:adminId, 'Admin', :now, :now, 'admin', null, null)
            ON CONFLICT (id) DO NOTHING;
            INSERT INTO user_roles (user_id, role_id)
            VALUES (:adminId, :adminId)
            ON CONFLICT (user_id, role_id) DO NOTHING;
            """
            )
    void createAdmin(@Param("adminId") UUID adminId, @Param("now") OffsetDateTime now);

    /**
     * Find the user with the given username
     * @param username to search for
     * @return user if it is found else empty
     */
    Optional<DeltaFiUser> findByUsername(String username);

    /**
     * Find the user with the given dn
     * @param dn to search for
     * @return user if it is found else empty
     */
    Optional<DeltaFiUser> findByDn(String dn);

    /**
     * Find all the users with the given usernames
     * @param usernames to search for
     * @return list of users that were found
     */
    List<DeltaFiUser> findByUsernameIn(Collection<String> usernames);

    /**
     * Find all the users with the given dns
     * @param dns to search for
     * @return list of users that were found
     */
    List<DeltaFiUser> findByDnIn(Collection<String> dns);

    /**
     * Check if the username exists
     * @param username to search for
     * @return true if a user with the given username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if the username exists for a user with a different id
     * @param id that should be excluded
     * @param username to search for
     * @return true if a user with the given username exists with another id
     */
    boolean existsByIdNotAndUsername(UUID id, String username);

    /**
     * Check if the dn exists
     * @param dn to search for
     * @return true if a user with the given dn exists
     */
    boolean existsByDn(String dn);

    /**
     * Check if the dn exists for a user with a different id
     * @param id that should be excluded
     * @param dn to search for
     * @return true if a user with the given dn exists with another id
     */
    boolean existsByIdNotAndDn(UUID id, String dn);

    /**
     * Check if the name exists
     * @param name to search for
     * @return true if a user with the given name exists
     */
    boolean existsByName(String name);

    /**
     * Check if the name exists for a user with a different id
     * @param id that should be excluded
     * @param name to search for
     * @return true if a user with the given name exists with another id
     */
    boolean existsByIdNotAndName(UUID id, String name);
}
