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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.PropertyId;
import org.deltafi.core.types.PropertyUpdate;
import org.deltafi.core.configuration.server.constants.PropertyConstants;
import org.deltafi.core.configuration.server.service.PropertyService;

import java.util.List;

@DgsComponent
public class PropertiesDatafetcher {

    private final PropertyService propertiesService;

    public PropertiesDatafetcher(PropertyService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @DgsQuery
    @NeedsPermission.SystemPropertiesRead
    public List<PropertySet> getPropertySets() {
        return propertiesService.getPopulatedProperties();
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public int updateProperties(@InputArgument List<PropertyUpdate> updates) {
        return propertiesService.updateProperties(updates);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public int removePropertyOverrides(@InputArgument List<PropertyId> propertyIds) {
        return propertiesService.unsetProperties(propertyIds);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean addPluginPropertySet(PropertySet propertySet) {
        validateMutation(propertySet.getId());
        propertiesService.saveProperties(propertySet);
        return true;
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
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
