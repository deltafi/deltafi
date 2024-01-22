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
package org.deltafi.core.services;

import org.deltafi.common.types.DataSourcePlan;
import org.deltafi.core.repo.DataSourcePlanRepo;
import org.deltafi.core.snapshot.types.DataSourceSnapshot;
import org.deltafi.core.types.DataSource;
import org.deltafi.core.validation.DataSourcePlanValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class DataSourcePlanService extends FlowPlanService<DataSourcePlan, DataSource, DataSourceSnapshot> {
    public DataSourcePlanService(DataSourcePlanValidator dataSourcePlanValidator, DataSourcePlanRepo flowPlanRepo, DataSourceService flowService, BuildProperties buildProperties) {
        super(dataSourcePlanValidator, flowPlanRepo, flowService, buildProperties);
    }
}
