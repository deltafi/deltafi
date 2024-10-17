/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.FlowType;

import java.util.List;

@Entity
@DiscriminatorValue("REST_DATA_SOURCE")
@EqualsAndHashCode(callSuper = true)
@Data
public class RestDataSource extends DataSource {
    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        return null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return List.of();
    }

    public RestDataSource() {
        super(FlowType.REST_DATA_SOURCE);
    }

    @Override
    public void copyFlowSpecificState(Flow sourceFlow) {
        if (sourceFlow instanceof RestDataSource restDataSource) {
            setMaxErrors(restDataSource.getMaxErrors());
        }
    }
}
