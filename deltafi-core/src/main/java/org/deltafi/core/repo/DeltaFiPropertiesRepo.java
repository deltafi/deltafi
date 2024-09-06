/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface DeltaFiPropertiesRepo extends JpaRepository<Property, String> {
    /**
     * Performs a batch upsert of properties and deletes properties not in the input or allowed list, all in a single database operation.
     *
     * @param keys Array of property keys to upsert.
     * @param defaultValues Array of default values corresponding to the keys.
     * @param descriptions Array of descriptions corresponding to the keys.
     * @param refreshables Array of boolean flags indicating if each property is refreshable.
     * @param allowedKeys Set of keys that are allowed to exist in the database. Properties with keys not in this set or in the input arrays will be deleted.
     *
     * @throws IllegalArgumentException if the lengths of keys, defaultValues, descriptions, and refreshables arrays are not equal.
     * @throws org.springframework.dao.DataAccessException if there's an issue executing the SQL query.
     *
     * @implNote This method assumes that the order of elements in keys, defaultValues, descriptions, and refreshables arrays correspond to each other.
     * @implNote The operation is executed within a transaction, ensuring atomicity of the entire operation.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value =
            "WITH input_rows(key, default_value, description, refreshable) AS ( " +
                    "    SELECT * FROM UNNEST(:keys, :defaultValues, :descriptions, :refreshables) " +
                    "), " +
                    "upsert AS ( " +
                    "    INSERT INTO properties (key, default_value, description, refreshable) " +
                    "    SELECT * FROM input_rows " +
                    "    ON CONFLICT (key) DO UPDATE SET " +
                    "        default_value = EXCLUDED.default_value, " +
                    "        description = EXCLUDED.description, " +
                    "        refreshable = EXCLUDED.refreshable " +
                    "    RETURNING key " +
                    ") " +
                    "DELETE FROM properties WHERE key NOT IN (SELECT key FROM upsert) AND key NOT IN :allowedKeys")
    void batchUpsertAndDeleteProperties(
            String[] keys,
            String[] defaultValues,
            String[] descriptions,
            Boolean[] refreshables,
            Set<String> allowedKeys
    );

    /**
     * Update each of the properties in the list where the key is the
     * property name and the value is the new custom value to use.
     * @param updates list of key value pairs
     * @return number of updated rows
     */
    @Modifying
    @Transactional
    @Query("UPDATE Property p SET p.customValue = :value WHERE p.key = :key AND COALESCE(p.customValue, 'FAKE_@_%#(*##*!!^') != :value")
    int updateProperty(String key, String value);

    /**
     * Null out the values for each of the given properties
     * @param propertyNames list of properties to unset
     * @return number of updated rows
     */
    @Modifying
    @Transactional
    @Query("UPDATE Property p SET p.customValue = NULL WHERE p.key IN :propertyNames AND p.customValue IS NOT NULL")
    int unsetProperties(List<String> propertyNames);
}
