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
package org.deltafi.core.services;

import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.OnErrorDataSourcePlan;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.repo.OnErrorDataSourceRepo;
import org.deltafi.core.types.snapshot.OnErrorDataSourceSnapshot;
import org.deltafi.core.types.OnErrorDataSource;
import org.deltafi.core.validation.OnErrorDataSourcePlanValidator;
import org.springframework.stereotype.Service;

@Service
public class OnErrorDataSourcePlanService extends FlowPlanService<OnErrorDataSourcePlan, OnErrorDataSource, OnErrorDataSourceSnapshot, OnErrorDataSourceRepo> {
    public OnErrorDataSourcePlanService(OnErrorDataSourcePlanValidator dataSourcePlanValidator, PluginRepository pluginRepo, OnErrorDataSourceService flowService) {
        super(dataSourcePlanValidator, pluginRepo, flowService, FlowType.ON_ERROR_DATA_SOURCE);
    }
}