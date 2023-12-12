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
package org.deltafi.common.rules;

import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleValidator {

    private final RuleEvaluator ruleEvaluator;

    public RuleValidator(RuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * Validate all the publish rules can be evaluated
     * @param publisher to validate
     * @return list of errors
     */
    public List<String> validatePublisher(Publisher publisher) {
        if (publisher == null || publisher.publishRules() == null || publisher.publishRules().getRules() == null) {
            return List.of();
        }

        return validateRules(publisher.publishRules().getRules().stream().map(Rule::getCondition).collect(Collectors.toSet()));
    }

    public List<String> validateSubscriber(Subscriber subscriber) {
        if (subscriber == null || subscriber.subscriptions() == null) {
            return List.of();
        }

        return validateRules(subscriber.subscriptions().stream().map(Rule::getCondition).collect(Collectors.toSet()));
    }

    /**
     * Validate of each of the conditions
     * @param conditions set of conditions to validate
     * @return list of errors
     */
    private List<String> validateRules(Set<String> conditions) {
        List<String> errors = new ArrayList<>();
        for (String condition : conditions) {
            try {
                ruleEvaluator.validateCondition(condition);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        return errors;
    }
}
