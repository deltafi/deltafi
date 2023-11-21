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
package org.deltafi.core.services.pubsub;

import org.deltafi.common.types.Rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SubscriberService {

    /**
     * Find the set of subscribers for the given topicId
     * @param topicId whose subscribers should be found
     * @return the set of subscribers for the given topicId
     */
    Set<Subscriber> subscriberForTopic(String topicId);

    /**
     * Get all running flows
     * @return list of running flows
     */
    List<? extends Subscriber> getRunningFlows();

    /**
     * Create a map of topicIds to the set of subscribers of that topic
     * using the running flows
     * @return map of topicIds to the set of subscribers of that topic
     */
    default Map<String, Set<Subscriber>> buildSubsriberMap() {
        Map<String, Set<Subscriber>> updatedMap = new HashMap<>();
        for (Subscriber subscriber : getRunningFlows()) {
            if (subscriber.subscriptions() != null) {
                for (Rule rule : subscriber.subscriptions()) {
                    for (String topic : rule.getTopics()) {
                        Set<Subscriber> subscribers = updatedMap.computeIfAbsent(topic, ignore -> new HashSet<>());
                        subscribers.add(subscriber);
                    }
                }
            }
        }
        return updatedMap;
    }
}
