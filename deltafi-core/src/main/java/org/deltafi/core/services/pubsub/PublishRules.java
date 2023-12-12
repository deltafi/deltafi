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

import lombok.Data;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class PublishRules {
    private String publisherName;
    private MatchingPolicy matchingPolicy;
    private DefaultRule defaultRule;
    private Set<Rule> rules = new HashSet<>();

    @Override
    public String toString() {
        return "Publisher Name: " + publisherName +
                "\nMatching Policy: " + matchingPolicy +
                "\nDefault Rule: " + defaultRule +
                "\nRules: [\n" +
                rules.stream().map(Rule::toString).collect(Collectors.joining(",\n")) +
                "\n]";
    }
}
