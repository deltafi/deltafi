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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DefaultBehavior;
import org.deltafi.common.types.DefaultRule;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.ErrorEvent;
import org.deltafi.common.types.FilterEvent;
import org.deltafi.common.types.MatchingPolicy;
import org.deltafi.common.types.PublishRules;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The PublisherService is used to find the next destinations for
 * a DeltaFile after it has been processed by a publisher
 */
@Slf4j
@Service
public class PublisherService {

    private static final DefaultRule ERROR_RULE = new DefaultRule(DefaultBehavior.ERROR);
    static final String NO_SUBSCRIBERS = "NO_SUBSCRIBERS";
    static final String NO_SUBSCRIBER_CAUSE = "No matching subscribers were found";

    private final RuleEvaluator ruleEvaluator;
    private final List<SubscriberService> subscriberServices;
    private final Clock clock;

    public PublisherService(RuleEvaluator ruleEvaluator, List<SubscriberService> subscriberServices, Clock clock) {
        this.ruleEvaluator = ruleEvaluator;
        this.subscriberServices = subscriberServices;
        this.clock = clock;
    }

    /**
     * Find the subscribers that should receive the DeltaFile.
     * Adds synthetic errors or filter actions based publisher and subscriber rules
     * that are applied. If there are no matching subscribers and no pending actions
     * the DeltaFile will be moved to a terminal state.
     * @param publisher publisher used to determine the DeltaFiles subscribers
     * @param deltaFile that will be sent to the matching subscribers
     * @return subscribers that should receive the DeltaFile next
     */
    public Set<Subscriber> subscribers(Publisher publisher, DeltaFile deltaFile) {
        Objects.requireNonNull(publisher, "The publisher cannot be null");
        PublishRules publishRules = publisher.publishRules();
        Objects.requireNonNull(publishRules, "The publish rules cannot be null");

        Set<Subscriber> subscribers = subscribers(getMatchingTopics(publishRules, deltaFile), deltaFile);

        if (subscribers.isEmpty()) {
            return handleNoMatches(publisher, deltaFile);
        }

        return subscribers;
    }

    private Set<Subscriber> subscribers(Set<String> topics, DeltaFile deltaFile) {
        // find all matching topics and map them to the set of subscribers
        Set<Subscriber> potentialSubscribers = topics.stream()
                    .map(this::getSubscribers)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

        // check the subscribers rules to determine if DeltaFile should go there
        return potentialSubscribers.stream()
                .filter(subscriber -> subscriberMatches(subscriber, deltaFile))
                .collect(Collectors.toSet());
    }

    /**
     * Evaluate the PublishRules to find the matching topics.
     * If no matching mode is present {@link MatchingPolicy#ALL_MATCHING} is used
     * @param publishRules contains the rules and policies used to find the matching topics
     * @param deltaFile to match against the PublishRules
     * @return set of the matching topics
     */
    Set<String> getMatchingTopics(PublishRules publishRules, DeltaFile deltaFile) {
        if (publishRules.getRules() == null) {
            return Set.of();
        }

        if (MatchingPolicy.FIRST_MATCHING.equals(publishRules.getMatchingPolicy())) {
            return publishRules.getRules().stream()
                    .filter(rule -> evaluateRule(rule, deltaFile))
                    .findFirst()
                    .map(Rule::getTopics).orElse(Set.of());
        }

        return publishRules.getRules().stream()
                .filter(rule -> evaluateRule(rule, deltaFile))
                .map(Rule::getTopics)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private boolean subscriberMatches(Subscriber subscriber, DeltaFile deltaFile) {
        if (subscriber.subscriptions() == null) {
            return false;
        }

        return subscriber.subscriptions().stream()
                .anyMatch(rule -> ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFile));
    }

    private Set<Subscriber> handleNoMatches(Publisher publisher, DeltaFile deltaFile) {
        PublishRules publishRules = publisher.publishRules();
        String publisherName = publisher.getName();
        DefaultRule defaultRule = Objects.requireNonNullElse(publishRules.getDefaultRule(), ERROR_RULE);
        DefaultBehavior defaultBehavior = defaultRule.getDefaultBehavior();

        if (DefaultBehavior.PUBLISH.equals(defaultBehavior)) {
            Set<Subscriber> subscribers = subscribers(Set.of(defaultRule.getTopic()), deltaFile);
            if (!subscribers.isEmpty()) {
                return subscribers;
            }

            // Fall back to error mode, the topic either didn't exist, filtered the DeltaFile or did not have subscribers that accepted the DeltaFile
            defaultBehavior = DefaultBehavior.ERROR;
        }

        String context = "No subscribers found using publisher `" + publisher.getName() + "`\n" + publishRules;
        if (DefaultBehavior.FILTER.equals(defaultBehavior)) {
            filterDeltaFile(deltaFile, publisherName, context);
        } else {
            errorDeltaFile(deltaFile, publisherName, context);
        }

        setStage(deltaFile);

        return Set.of();
    }

    private Set<Subscriber> getSubscribers(String topic) {
        return subscriberServices.stream()
                .map(subscriberService -> subscriberService.subscriberForTopic(topic))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private void setStage(DeltaFile deltaFile) {
        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }
    }

    private boolean evaluateRule(Rule rule, DeltaFile deltaFile) {
        return ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFile);
    }

    private void errorDeltaFile(DeltaFile deltaFile, String publisherName, String context) {
        // grab the last content list to copy into the synthetic error action to make it available for retry
        List<Content> toCopy = deltaFile.lastContent();
        queueSyntheticAction(deltaFile, publisherName);
        deltaFile.errorAction(buildErrorEvent(deltaFile, publisherName, context));
        deltaFile.lastAction().setContent(toCopy);
    }

    private void filterDeltaFile(DeltaFile deltaFile, String publisherName, String context) {
        queueSyntheticAction(deltaFile, publisherName);
        deltaFile.filterAction(buildFilterEvent(deltaFile, publisherName, context));
        deltaFile.setFiltered(true);
    }

    private void queueSyntheticAction(DeltaFile deltaFile, String publisherName) {
        deltaFile.queueNewAction(publisherName, NO_SUBSCRIBERS, ActionType.PUBLISH, false);
    }

    private ActionEvent buildFilterEvent(DeltaFile deltaFile, String publisherName, String context) {
        return buildActionEvent(deltaFile, publisherName)
                .filter(FilterEvent.builder().message(NO_SUBSCRIBER_CAUSE).context(context).build())
                .build();
    }

    private ActionEvent buildErrorEvent(DeltaFile deltaFile, String publisherName, String context) {
        return buildActionEvent(deltaFile, publisherName)
                .error(ErrorEvent.builder().cause(NO_SUBSCRIBER_CAUSE).context(context).build())
                .build();
    }

    private ActionEvent.ActionEventBuilder buildActionEvent(DeltaFile deltaFile, String publisherName) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(publisherName)
                .action(NO_SUBSCRIBERS)
                .start(now)
                .stop(now)
                .type(ActionEventType.PUBLISH);
    }
}
