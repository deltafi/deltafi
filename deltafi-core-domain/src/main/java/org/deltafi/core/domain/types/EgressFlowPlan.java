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
package org.deltafi.core.domain.types;

import lombok.Data;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.EgressActionConfiguration;
import org.deltafi.core.domain.generated.types.EnrichActionConfiguration;
import org.deltafi.core.domain.generated.types.FormatActionConfiguration;
import org.deltafi.core.domain.generated.types.ValidateActionConfiguration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document("egressFlowPlan")
public class EgressFlowPlan {
    @Id
    private String name;
    private String description;
    private PluginCoordinates sourcePlugin;
    private EgressActionConfiguration egressAction;
    private FormatActionConfiguration formatAction;
    private List<EnrichActionConfiguration> enrichActions;
    private List<ValidateActionConfiguration> validateActions;
    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;
}
