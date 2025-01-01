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
package org.deltafi.core.services.pubsub;

import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SubscriberService {

    /**
     * Find the set of subscribers for the given topicId
     * @param topic whose subscribers should be found
     * @return the set of subscribers for the given topicId
     */
    Set<Subscriber> subscriberForTopic(String topic);

    /**
     * Get all running and paused flows
     * @return list of running and paused flows
     */
    List<? extends Subscriber> getActiveFlows();

    /**
     * Create a map of topicIds to the set of subscribers of that topic
     * using the running flows
     * @return map of topicIds to the set of subscribers of that topic
     */
    default Map<String, Set<Subscriber>> buildSubscriberMap() {
        Map<String, Set<Subscriber>> updatedMap = new HashMap<>();
        for (Subscriber subscriber : getActiveFlows()) {
            if (subscriber.subscribeRules() != null) {
                for (Rule rule : subscriber.subscribeRules()) {
                    Set<Subscriber> subscribers = updatedMap.computeIfAbsent(rule.getTopic(), ignore -> new HashSet<>());
                    subscribers.add(subscriber);
                }
            }
        }
        return updatedMap;
    }
}
