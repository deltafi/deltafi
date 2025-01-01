/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.PropertySet;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.UiLinkService;

import java.util.List;
import java.util.UUID;

@DgsComponent
@RequiredArgsConstructor
public class DeltaFiPropertiesDatafetcher {

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final UiLinkService uiLinkService;
    private final CoreAuditLogger auditLogger;

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
    public boolean updateProperties(@InputArgument List<KeyValue> updates) {
        auditLogger.audit("updated properties: {}", CoreAuditLogger.listToString(updates));
        return deltaFiPropertiesService.updateProperties(updates);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean removePropertyOverrides(@InputArgument List<String> propertyNames) {
        auditLogger.audit("removed property overrides from properties: {}", String.join(", ", propertyNames));
        return deltaFiPropertiesService.unsetProperties(propertyNames);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public Link saveLink(@InputArgument Link link) {
        auditLogger.audit("saved link {}", link);
        return uiLinkService.saveLink(link);
    }

    @DgsMutation
    @NeedsPermission.SystemPropertiesUpdate
    public boolean removeLink(@InputArgument UUID id) {
        auditLogger.audit("removed link {}", id);
        return uiLinkService.removeLink(id);
    }
}
