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

import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.Topic;
import org.deltafi.core.generated.types.TopicParticipant;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.DataSource;
import org.deltafi.core.types.Flow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlowCacheService {
    private final FlowRepo flowRepo;
    private volatile Map<FlowKey, Flow> flowCache;

    private record FlowKey(String name, FlowType type) {}

    public void refreshCache() {
        flowCache = flowRepo.findAll().stream()
                .collect(Collectors.toMap(
                        flow -> new FlowKey(flow.getName(), flow.getType()),
                        flow -> flow
                ));
    }

    public List<Flow> flowsOfType(FlowType flowType) {
        return flowCache.entrySet().stream()
                .filter(entry -> entry.getKey().type() == flowType)
                .map(Map.Entry::getValue)
                .toList();
    }

    public List<String> getNamesOfInvalidFlows(FlowType flowType) {
        return flowsOfType(flowType).stream()
                .filter(Flow::isInvalid)
                .map(Flow::getName)
                .toList();
    }

    public Flow getFlow(FlowType flowType, String flowName) {
        return flowCache.getOrDefault(new FlowKey(flowName, flowType), null);
    }

    public Flow getRunningFlow(FlowType flowType, String flowName) {
        Flow flow = getFlow(flowType, flowName);
        if (flow == null || !flow.isRunning()) {
            return null;
        }

        return flow;
    }

    public Flow getFlowOrThrow(FlowType flowType, String flowName) {
        Flow flow = getFlow(flowType, flowName);
        if (flow == null) {
            throw new IllegalArgumentException("No " + flowType + " flow exists with the name: " + flowName);
        }

        return flow;
    }

    public List<Topic> getTopics() {
        Map<String, Topic> topicMap = new HashMap<>();
        for (Flow flow : flowCache.values()) {

            if (flow instanceof DataSource dataSource) {
                addPublisher(topicMap, dataSource.getTopic(), flow, null);
            }

            if (flow instanceof Publisher publisher) {
                for (Rule rule : publisher.publishRules().getRules()) {
                    addPublisher(topicMap, rule.getTopic(), flow, rule.getCondition());
                }
                if (publisher.publishRules().getDefaultRule().getDefaultBehavior() == DefaultBehavior.PUBLISH) {
                    String topicName = publisher.publishRules().getDefaultRule().getTopic();
                    addPublisher(topicMap, topicName, flow, null);
                }
            }

            if (flow instanceof Subscriber subscriber) {
                for (Rule rule : subscriber.subscribeRules()) {
                    addSubscriber(topicMap, rule.getTopic(), flow, rule.getCondition());
                }
            }
        }

        for (Topic topic : topicMap.values()) {
            topic.getPublishers().sort(Comparator.comparing(TopicParticipant::getName));
            topic.getSubscribers().sort(Comparator.comparing(TopicParticipant::getName));
        }

        return new ArrayList<>(topicMap.values().stream().sorted(Comparator.comparing(Topic::getName)).toList());
    }

    private TopicParticipant createParticipant(Flow flow, String condition) {
        return TopicParticipant.newBuilder()
                .name(flow.getName())
                .type(flow.getType())
                .state(flow.getFlowStatus().getState())
                .condition(condition)
                .build();
    }

    private void addPublisher(Map<String, Topic> topicMap, String topicName, Flow flow, String condition) {
        Topic topic = topicMap.getOrDefault(topicName, new Topic(topicName, new ArrayList<>(), new ArrayList<>()));
        TopicParticipant topicParticipant = createParticipant(flow, condition);
        topic.getPublishers().add(topicParticipant);
        topicMap.put(topicName, topic);
    }

    private void addSubscriber(Map<String, Topic> topicMap, String topicName, Flow flow, String condition) {
        Topic topic = topicMap.getOrDefault(topicName, new Topic(topicName, new ArrayList<>(), new ArrayList<>()));
        TopicParticipant topicParticipant = createParticipant(flow, condition);
        topic.getSubscribers().add(topicParticipant);
        topicMap.put(topicName, topic);
    }
}
