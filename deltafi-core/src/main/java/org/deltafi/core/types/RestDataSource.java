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
package org.deltafi.core.types;

import lombok.Data;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFiConfiguration;
import org.deltafi.common.types.RestDataSourceConfiguration;
import org.deltafi.core.generated.types.ActionFamily;

import java.util.EnumMap;
import java.util.List;

@Data
public class RestDataSource extends DataSource {

    public RestDataSource() {
        super("REST_DATA_SOURCE");
    }
    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        return null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return List.of();
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType) {
        return List.of();
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        // nothing to do here
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        RestDataSourceConfiguration restDataSourceConfiguration = new RestDataSourceConfiguration(name);
        restDataSourceConfiguration.setTopic(getTopic());
        return restDataSourceConfiguration;
    }

    @Override
    public void copyFields(DataSource sourceDataSource) {
        if (sourceDataSource instanceof RestDataSource restDataSource) {
            setTopic(restDataSource.getTopic());
        }
    }
}
