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
package org.deltafi.common.rules;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RuleValidator {

    private final RuleEvaluator ruleEvaluator;

    public RuleValidator(RuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * Validate that publish rules are set and the rules can be evaluated
     * @param publisher to validate
     * @return list of errors
     */
    public List<String> validatePublisher(Publisher publisher) {
        if (publisher == null) {
            return List.of("Publisher was null");
        } else if (publisher.publishRules() == null) {
            return List.of("Publisher was missing publish section");
        } else if (missingRules(publisher.publishRules().getRules())) {
            return List.of("Publisher was missing publish rules");
        }

        return validateRules(publisher.publishRules().getRules());
    }

    /**
     * Validate that the subscribe rules are set and the rules can be evaluated
     * @param subscriber to validate
     * @return list of errors
     */
    public List<String> validateSubscriber(Subscriber subscriber) {
        if (subscriber == null) {
            return List.of("Subscriber was null");
        } else if (missingRules(subscriber.subscribeRules())) {
            return List.of("Subscriber was missing subscribe rules");
        }

        return validateRules(subscriber.subscribeRules());
    }

    private boolean missingRules(Collection<Rule> rules) {
        return rules == null || rules.isEmpty();
    }

    private List<String> validateRules(Collection<Rule> rules) {
        List<String> errors = new ArrayList<>();
        for (Rule rule : rules) {
            if (StringUtils.isBlank(rule.getTopic())) {
                errors.add("Rules must provide a topic");
            }
            Optional.ofNullable(validateCondition(rule.getCondition())).ifPresent(errors::add);
        }
        return errors;
    }

    /**
     * Validate of each of the conditions
     * @param condition condition to validate
     * @return list of errors
     */
    private String validateCondition(String condition) {
        try {
            ruleEvaluator.validateCondition(condition);
        } catch (Exception e) {
            return e.getMessage();
        }

        return null;
    }
}
