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

import jakarta.transaction.Transactional;
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
     * Remove any property entry whose key is not found in the given set of property names
     * @param propertyKeys valid property names to keep
     */
    void deleteByKeyNotIn(Set<String> propertyKeys);

    /**
     * Insert the properties if they do not exist. If they do exist
     * update the default values and description if necessary.
     * @param properties list of properties to upsert
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO properties (key, default_value, description, refreshable) " +
            "VALUES (:#{#prop.key}, :#{#prop.defaultValue}, :#{#prop.description}, :#{#prop.refreshable}) " +
            "ON CONFLICT (key) DO UPDATE SET " +
            "default_value = :#{#prop.defaultValue}, " +
            "description = :#{#prop.description}, " +
            "refreshable = :#{#prop.refreshable}",
            nativeQuery = true)
    void upsertProperties(Property prop);

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
