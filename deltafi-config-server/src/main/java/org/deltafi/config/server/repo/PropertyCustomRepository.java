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
package org.deltafi.config.server.repo;

import org.deltafi.config.server.api.domain.PropertyId;
import org.deltafi.config.server.api.domain.PropertyUpdate;

import java.util.List;
import java.util.Set;

public interface PropertyCustomRepository {

    /**
     * Get a set of all id's in the collection.
     *
     * @return - all PropertySet ids
     */
    Set<String> getIds();

    /**
     * Apply the given updates to the collection
     * @param updates - list of updates to apply
     * @return - number of property sets that were updated
     */
    int updateProperties(List<PropertyUpdate> updates);

    /**
     * Unset the value field for each property in the list
     * @param propertyIds - list of properties that need to be unset
     * @return - number of property sets that were updated
     */
    int unsetProperties(List<PropertyId> propertyIds);

}
