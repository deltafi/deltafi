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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.converters.RestDataSourcePlanConverter;
import org.deltafi.core.repo.RestDataSourceRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.RestDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.RestDataSourceValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RestDataSourceService extends FlowService<RestDataSourcePlanEntity, RestDataSource, RestDataSourceSnapshot, RestDataSourceRepo> {

    private static final RestDataSourcePlanConverter REST_DATA_SOURCE_FLOW_PLAN_CONVERTER = new RestDataSourcePlanConverter();

    public RestDataSourceService(RestDataSourceRepo restDataSourceRepo, PluginVariableService pluginVariableService,
                                 RestDataSourceValidator restDataSourceValidator, BuildProperties buildProperties) {
        super("dataSource", restDataSourceRepo, pluginVariableService, REST_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                restDataSourceValidator, buildProperties);
    }

    @Override
    void copyFlowSpecificFields(RestDataSource sourceFlow, RestDataSource targetFlow) {
        sourceFlow.copyFields(targetFlow);
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        List<RestDataSourceSnapshot> restDataSourceSnapshots = new ArrayList<>();
        for (RestDataSource dataSource : getAll()) {
            restDataSourceSnapshots.add(new RestDataSourceSnapshot(dataSource));
        }
        systemSnapshot.setRestDataSources(restDataSourceSnapshots);
    }

    @Override
    public List<RestDataSourceSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getRestDataSources();
    }

    @Override
    protected Class<RestDataSource> getFlowClass() {
        return RestDataSource.class;
    }

    @Override
    protected Class<RestDataSourcePlanEntity> getFlowPlanClass() {
        return RestDataSourcePlanEntity.class;
    }

    @Override
    protected FlowType getFlowType() {
        return FlowType.REST_DATA_SOURCE;
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(RestDataSource flow, RestDataSourceSnapshot dataSourceSnapshot, Result result) {
        boolean changed = false;
        if (!Objects.equals(flow.getTopic(), dataSourceSnapshot.getTopic())) {
            flow.setTopic(dataSourceSnapshot.getTopic());
            changed = true;
        }
        return changed;
    }
}
