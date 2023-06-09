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
package org.deltafi.core.plugin.generator.flows;

import org.deltafi.common.types.ActionConfiguration;

import java.util.function.Predicate;

public class ActionConfigMatchers {

    public static final ActionConfigMatcher DEFAULT_LOAD_MATCHER = new ActionConfigMatcher("LoadAction", "org.deltafi.passthrough.action.RoteLoadAction");
    public static final ActionConfigMatcher DEFAULT_FORMAT_MATCHER = new ActionConfigMatcher("FormatAction", "org.deltafi.passthrough.action.RoteFormatAction");
    public static final ActionConfigMatcher DEFAULT_EGRESS_MATCHER = new ActionConfigMatcher("EgressAction", "org.deltafi.core.action.RestPostEgressAction");

    public record ActionConfigMatcher(String name, String fullClass) implements Predicate<ActionConfiguration> {
        @Override
        public boolean test(ActionConfiguration actionConfiguration) {
            return name.equals(actionConfiguration.getName()) && fullClass.equals(actionConfiguration.getType());
        }
    }
}
