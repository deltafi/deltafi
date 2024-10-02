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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishRules {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());
    private static final DefaultRule ERROR_RULE = new DefaultRule(DefaultBehavior.ERROR);

    private MatchingPolicy matchingPolicy;
    private DefaultRule defaultRule;
    private List<Rule> rules;

    public void setMatchingPolicy(MatchingPolicy matchingPolicy) {
        this.matchingPolicy = Objects.requireNonNullElse(matchingPolicy, MatchingPolicy.ALL_MATCHING);
    }

    public void setDefaultRule(DefaultRule defaultRule) {
        this.defaultRule = Objects.requireNonNullElse(defaultRule, ERROR_RULE);
    }

    public void setRules(List<Rule> rules) {
        this.rules = Objects.requireNonNullElseGet(rules, ArrayList::new);
    }

    @Override
    public String toString() {
        try {
            return YAML_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return simpleToString();
        }
    }

    String simpleToString() {
        return "PublishRules{" +
                "matchingPolicy=" + matchingPolicy +
                ", defaultRule=" + defaultRule +
                ", rules=" + rules +
                '}';
    }
}
