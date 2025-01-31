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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DataSinkPlan;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.converters.DataSinkPlanConverter;
import org.deltafi.core.repo.DataSinkRepo;
import org.deltafi.core.services.pubsub.SubscriberService;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.DataSinkSnapshot;
import org.deltafi.core.types.DataSink;

import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public
class DataSinkService extends FlowService<DataSinkPlan, DataSink, DataSinkSnapshot, DataSinkRepo> implements SubscriberService {

    private static final DataSinkPlanConverter EGRESS_FLOW_PLAN_CONVERTER = new DataSinkPlanConverter();

    private Map<String, Set<Subscriber>> topicSubscribers;

    public DataSinkService(DataSinkRepo flowRepo, PluginVariableService pluginVariableService,
                           FlowValidator flowValidator, BuildProperties buildProperties,
                           FlowCacheService flowCacheService, EventService eventService) {
        super(FlowType.DATA_SINK, flowRepo, pluginVariableService, EGRESS_FLOW_PLAN_CONVERTER,
                flowValidator, buildProperties, flowCacheService,
                eventService, DataSink.class, DataSinkPlan.class);
    }

    @Override
    public void onRefreshCache() {
        topicSubscribers = buildSubscriberMap();
    }

    /**
     * Sets the expected set of annotations for a given dataSource, identified by its name. If the update is successful,
     * the method refreshes the cache and returns true.
     *
     * @param flowName The name of the dataSink to update, represented as a {@code String}.
     * @param expectedAnnotations The new set of expected annotations to be set for the specified dataSink, as an {@code set}
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false)
     */
    public boolean setExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        DataSink flow = getFlowOrThrow(flowName);

        if (Objects.equals(expectedAnnotations, flow.getExpectedAnnotations())) {
            log.warn("Tried to update the expected annotations on dataSink {} to the same set of annotations: {}", flowName, expectedAnnotations);
            return false;
        }

        if (flowRepo.updateExpectedAnnotations(flowName, expectedAnnotations) > 0) {
            refreshCache();
            return true;
        }

        return false;
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setDataSinks(getAll().stream().map(DataSinkSnapshot::new).toList());
    }

    @Override
    public List<DataSinkSnapshot> getFlowSnapshots(Snapshot snapshot) {
        return snapshot.getDataSinks();
    }

    @Override
    public Set<Subscriber> subscriberForTopic(String topic) {
        return topicSubscribers.getOrDefault(topic, Set.of());
    }
}
