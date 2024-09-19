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

import org.deltafi.core.types.Role;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepoCustom {

    /**
     * Remove the role with th given id and remove that role from
     * all users that previously had it.
     * @param id of the role to remove
     * @return the role that was removed or empty if it did not exist
     */
    Optional<Role> deleteRoleAndAssociationsById(UUID id);
}
