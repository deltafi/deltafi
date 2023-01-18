/**
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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.types.PropertyId;
import org.deltafi.core.types.PropertyUpdate;

import java.util.List;

@DgsComponent
public class DeltaFiPropertiesDatafetcher {

    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public DeltaFiPropertiesDatafetcher(DeltaFiPropertiesService deltaFiPropertiesService) {
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    @DgsQuery
    @NeedsPermission.SystemPropertiesRead
    public List<PropertySet> getPropertySets() {
        return deltaFiPropertiesService.getPopulatedProperties();
    }


    @DgsQuery
    @NeedsPermission.SystemPropertiesRead
    public DeltaFiProperties getDeltaFiProperties() {
        return deltaFiPropertiesService.getDeltaFiProperties();
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean updateProperties(@InputArgument List<PropertyUpdate> updates) {
        return deltaFiPropertiesService.updateProperties(updates);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean removePropertyOverrides(@InputArgument List<PropertyId> propertyIds) {
        return deltaFiPropertiesService.unsetProperties(propertyIds);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean saveExternalLink(@InputArgument Link link) {
        return deltaFiPropertiesService.saveExternalLink(link);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean saveDeltaFileLink(@InputArgument Link link) {
        return deltaFiPropertiesService.saveDeltaFileLink(link);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean removeExternalLink(@InputArgument String linkName) {
        return deltaFiPropertiesService.removeExternalLink(linkName);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean removeDeltaFileLink(@InputArgument String linkName) {
        return deltaFiPropertiesService.removeDeltaFileLink(linkName);
    }

}
