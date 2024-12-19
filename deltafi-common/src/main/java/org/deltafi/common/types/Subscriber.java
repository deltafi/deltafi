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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Set;

/**
 * Interface for things that subscribe to topics
 */
public interface Subscriber {

    /**
     * Get the set of subscription rules
     * @return set of subscription rules
     */
    Set<Rule> subscribeRules();

    /**
     * Get the name of this subscriber
     * @return the name of this subscriber
     */
    String getName();

    /**
     * Get the FlowType of the subscriber
     * @return the FlowType
     */
    FlowType flowType();

    List<ActionConfiguration> allActionConfigurations();

    /**
     *
     * @return if the subscriber is in test mode
     */
    @JsonIgnore
    default boolean isTestMode() {
        return false;
    }

    boolean isPaused();
}
