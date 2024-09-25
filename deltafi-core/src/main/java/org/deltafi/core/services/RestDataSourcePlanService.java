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
package org.deltafi.core.services;

import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.RestDataSourcePlan;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.repo.RestDataSourceRepo;
import org.deltafi.core.types.snapshot.RestDataSourceSnapshot;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.validation.RestDataSourcePlanValidator;
import org.springframework.stereotype.Service;

@Service
public class RestDataSourcePlanService extends FlowPlanService<RestDataSourcePlan, RestDataSource, RestDataSourceSnapshot, RestDataSourceRepo> {
    public RestDataSourcePlanService(RestDataSourcePlanValidator dataSourcePlanValidator, PluginRepository pluginRepo, RestDataSourceService flowService) {
        super(dataSourcePlanValidator, pluginRepo, flowService, FlowType.REST_DATA_SOURCE, RestDataSourcePlan.class);
    }
}
