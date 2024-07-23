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

import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.Property;

import java.util.List;

public interface DeltaFiPropertiesRepoCustom {

    /**
     * Insert the properties if they do not exist. If they do exist
     * update the default values and description if necessary.
     * @param properties list of properties to upsert
     */
    void upsertProperties(List<Property> properties);

    /**
     * Update each of the properties in the list where the key is the
     * property name and the value is the new custom value to use.
     * @param updates list of key value pairs
     * @return true for success
     */
    boolean updateProperties(List<KeyValue> updates);

    /**
     * Null out the values for each of the given properties
     * @param propertyNames list of properties to unset
     * @return true for a success
     */
    boolean unsetProperties(List<String> propertyNames);
}
