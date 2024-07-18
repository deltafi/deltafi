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

import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.FlowPlanRepo;
import org.deltafi.core.repo.TimedDataSourceRepo;
import org.deltafi.core.snapshot.types.TimedDataSourceSnapshot;
import org.deltafi.core.types.TimedDataSource;
import org.deltafi.core.types.TimedDataSourcePlanEntity;
import org.deltafi.core.validation.TimedDataSourcePlanValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class TimedDataSourcePlanService extends FlowPlanService<TimedDataSourcePlanEntity, TimedDataSource, TimedDataSourceSnapshot, TimedDataSourceRepo> {
    public TimedDataSourcePlanService(TimedDataSourcePlanValidator dataSourcePlanValidator, FlowPlanRepo flowPlanRepo, TimedDataSourceService flowService, BuildProperties buildProperties) {
        super(dataSourcePlanValidator, flowPlanRepo, flowService, buildProperties);
    }

    @Override
    protected FlowType getFlowType() {
        return FlowType.TIMED_DATA_SOURCE;
    }

    @Override
    protected Class<TimedDataSourcePlanEntity> getFlowPlanClass() {
        return TimedDataSourcePlanEntity.class;
    }
}
