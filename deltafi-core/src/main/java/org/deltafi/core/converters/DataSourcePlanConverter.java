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
package org.deltafi.core.converters;

import org.deltafi.common.types.RestDataSourcePlan;
import org.deltafi.common.types.TimedDataSourcePlan;
import org.deltafi.common.types.TimedIngressActionConfiguration;
import org.deltafi.common.types.DataSourcePlan;
import org.deltafi.core.types.DataSource;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.types.TimedDataSource;
import org.springframework.scheduling.support.CronExpression;

import java.time.OffsetDateTime;

public class DataSourcePlanConverter extends FlowPlanConverter<DataSourcePlan, DataSource> {

    @Override
    public DataSource createFlow(DataSourcePlan dataSourcePlan, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        if (dataSourcePlan instanceof TimedDataSourcePlan timedDataSourcePlan) {
            TimedDataSource timedDataSource = new TimedDataSource();
            timedDataSource.setTopic(dataSourcePlan.getTopic());
            populateTimedDataSource(timedDataSourcePlan, timedDataSource, flowPlanPropertyHelper);
            return timedDataSource;
        } else if (dataSourcePlan instanceof RestDataSourcePlan restDataSourcePlan) {
            RestDataSource restDataSource = new RestDataSource();
            restDataSource.setTopic(dataSourcePlan.getTopic());
            return restDataSource;
        }

        throw new IllegalArgumentException("Unexpected flow plan type: " + dataSourcePlan.getClass().getSimpleName());
    }

    void populateTimedDataSource(TimedDataSourcePlan timedDataSourcePlan, TimedDataSource timedDataSource, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        timedDataSource.setTimedIngressAction(buildTimedIngressAction(timedDataSourcePlan.getTimedIngressAction(), flowPlanPropertyHelper));
        timedDataSource.setCronSchedule(timedDataSourcePlan.getCronSchedule());
        CronExpression cronExpression = CronExpression.parse(timedDataSourcePlan.getCronSchedule());
        timedDataSource.setNextRun(cronExpression.next(OffsetDateTime.now()));
    }

    /**
     * Return a copy of the timed ingress action configuration with placeholders resolved where possible.
     *
     * @param timedIngressActionTemplate template of the TimedIngressActionConfiguration that should be created
     * @return TimedIngressActionConfiguration with variable values substituted in
     */
    TimedIngressActionConfiguration buildTimedIngressAction(TimedIngressActionConfiguration timedIngressActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        TimedIngressActionConfiguration timedIngressActionConfiguration = new TimedIngressActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(timedIngressActionTemplate), timedIngressActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(timedIngressActionConfiguration, timedIngressActionTemplate);
        return timedIngressActionConfiguration;
    }
}