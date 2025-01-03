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
package org.deltafi.common.types;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Rule {
    private String topic;
    private String condition;

    /**
     * A rule consists of condition that will be evaluated against a DeltaFile
     * and a topic to use if the condition evaluates to true
     * @param topic to use if the condition matches
     * @param condition condition that must be true to use the topic
     */
    public Rule(String topic, String condition) {
        this.topic = topic;
        this.condition = condition;
    }

    public Rule(String topic) {
        this(topic, null);
    }
}
