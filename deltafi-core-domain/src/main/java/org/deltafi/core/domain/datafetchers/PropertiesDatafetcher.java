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
package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.deltafi.core.domain.api.types.PropertyId;
import org.deltafi.core.domain.api.types.PropertySet;
import org.deltafi.core.domain.api.types.PropertyUpdate;
import org.deltafi.core.config.server.constants.PropertyConstants;
import org.deltafi.core.config.server.service.PropertyService;

import java.util.List;

@DgsComponent
public class PropertiesDatafetcher {

    private final PropertyService propertiesService;

    public PropertiesDatafetcher(PropertyService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @DgsQuery
    public List<PropertySet> getPropertySets() {
        return propertiesService.getPopulatedProperties();
    }

    @DgsMutation
    public int updateProperties(@InputArgument(collectionType = PropertyUpdate.class) List<PropertyUpdate> updates) {
        return propertiesService.updateProperties(updates);
    }

    @DgsMutation
    public int removePropertyOverrides(@InputArgument(collectionType = PropertyId.class) List<PropertyId> propertyIds) {
        return propertiesService.unsetProperties(propertyIds);
    }

    @DgsMutation
    public boolean addPluginPropertySet(PropertySet propertySet) {
        validateMutation(propertySet.getId());
        propertiesService.saveProperties(propertySet);
        return true;
    }

    @DgsMutation
    public boolean removePluginPropertySet(String propertySetId) {
        validateMutation(propertySetId);
        return propertiesService.removeProperties(propertySetId);
    }

    private void validateMutation(String propertySetId) {
        if (PropertyConstants.ACTION_KIT_PROPERTY_SET.equals(propertySetId) || PropertyConstants.DELTAFI_PROPERTY_SET.equals(propertySetId)) {
            throw new IllegalArgumentException("Core PropertySet: " + propertySetId + " cannot be added, replaced or removed");
        }
    }

}
