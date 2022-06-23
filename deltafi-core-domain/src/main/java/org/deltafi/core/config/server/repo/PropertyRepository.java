/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.config.server.repo;

import org.deltafi.core.domain.api.types.PropertyId;
import org.deltafi.core.domain.api.types.PropertySet;
import org.deltafi.core.domain.api.types.PropertyUpdate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PropertyRepository {

    /**
     * Save the given property set
     * @param propertySet to save
     * @return saved property set
     */
    PropertySet save(PropertySet propertySet);

    /**
     * Save all the given property sets
     * @param propertySets to save
     * @return saved property sets
     */
    List<PropertySet> saveAll(Collection<PropertySet> propertySets);

    /**
     * Find a property set by the given id if it exists
     * @param id to search for
     * @return the property set if it exists or empty
     */
    Optional<PropertySet> findById(String id);

    /**
     * Find all the stored property sets
     * @return
     */
    List<PropertySet> findAll();

    /**
     * Delete the property set with the given id
     * @param id of the property set to remove
     * @return true if the property set existed and was removed
     */
    boolean removeById(String id);

    /**
     * Remove all the property sets
     */
    void removeAll();

    /**
     * Get a set of all id's in the collection.
     * @return all PropertySet ids
     */
    Set<String> getIds();

    /**
     * Apply the given updates to the collection
     * @param updates - list of updates to apply
     * @return number of property sets that were updated
     */
    int updateProperties(List<PropertyUpdate> updates);

    /**
     * Unset the value field for each property in the list
     * @param propertyIds - list of properties that need to be unset
     * @return number of property sets that were updated
     */
    int unsetProperties(List<PropertyId> propertyIds);
}
