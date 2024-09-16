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

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class DefaultRule {
    private DefaultBehavior defaultBehavior;
    private String topic;

    /**
     * Rule used when no subscribers are found for a DeltaFile
     * @param defaultBehavior that should be used when there are no subscribers found
     * @param topic to publish to, only relevant to {@link DefaultBehavior#PUBLISH}
     */
    public DefaultRule(DefaultBehavior defaultBehavior, String topic) {
        this.defaultBehavior = defaultBehavior;
        this.topic = topic;
    }

    public DefaultRule(DefaultBehavior defaultBehavior) {
        this(defaultBehavior, null);
    }
}
