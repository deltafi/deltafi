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
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.Flow;

/**
 * DataFetcher for flow-related computed fields.
 */
@DgsComponent
@RequiredArgsConstructor
public class FlowDataFetcher {

    private final PluginService pluginService;

    @DgsData(parentType = "RestDataSource", field = "pluginReady")
    public boolean restDataSourcePluginReady(DgsDataFetchingEnvironment dfe) {
        return isPluginReady(dfe.getSource());
    }

    @DgsData(parentType = "TimedDataSource", field = "pluginReady")
    public boolean timedDataSourcePluginReady(DgsDataFetchingEnvironment dfe) {
        return isPluginReady(dfe.getSource());
    }

    @DgsData(parentType = "OnErrorDataSource", field = "pluginReady")
    public boolean onErrorDataSourcePluginReady(DgsDataFetchingEnvironment dfe) {
        return isPluginReady(dfe.getSource());
    }

    @DgsData(parentType = "TransformFlow", field = "pluginReady")
    public boolean transformFlowPluginReady(DgsDataFetchingEnvironment dfe) {
        return isPluginReady(dfe.getSource());
    }

    @DgsData(parentType = "DataSink", field = "pluginReady")
    public boolean dataSinkPluginReady(DgsDataFetchingEnvironment dfe) {
        return isPluginReady(dfe.getSource());
    }

    @DgsData(parentType = "RestDataSource", field = "pluginNotReadyReason")
    public String restDataSourcePluginNotReadyReason(DgsDataFetchingEnvironment dfe) {
        return getPluginNotReadyReason(dfe.getSource());
    }

    @DgsData(parentType = "TimedDataSource", field = "pluginNotReadyReason")
    public String timedDataSourcePluginNotReadyReason(DgsDataFetchingEnvironment dfe) {
        return getPluginNotReadyReason(dfe.getSource());
    }

    @DgsData(parentType = "OnErrorDataSource", field = "pluginNotReadyReason")
    public String onErrorDataSourcePluginNotReadyReason(DgsDataFetchingEnvironment dfe) {
        return getPluginNotReadyReason(dfe.getSource());
    }

    @DgsData(parentType = "TransformFlow", field = "pluginNotReadyReason")
    public String transformFlowPluginNotReadyReason(DgsDataFetchingEnvironment dfe) {
        return getPluginNotReadyReason(dfe.getSource());
    }

    @DgsData(parentType = "DataSink", field = "pluginNotReadyReason")
    public String dataSinkPluginNotReadyReason(DgsDataFetchingEnvironment dfe) {
        return getPluginNotReadyReason(dfe.getSource());
    }

    private boolean isPluginReady(Object source) {
        if (source instanceof Flow flow) {
            return pluginService.isPluginReady(flow.getSourcePlugin());
        }
        return true;
    }

    private String getPluginNotReadyReason(Object source) {
        if (source instanceof Flow flow) {
            return pluginService.getPluginNotReadyReason(flow.getSourcePlugin());
        }
        return null;
    }
}
