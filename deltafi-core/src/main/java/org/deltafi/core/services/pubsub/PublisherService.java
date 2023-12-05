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
import org.deltafi.common.types.Topic;
import org.deltafi.common.types.TopicFilterPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
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

    private final RuleEvaluator ruleEvaluator;
    private final TopicService topicService;
    private final Clock clock;

    public PublisherService(RuleEvaluator ruleEvaluator, TopicService topicService, Clock clock) {
        this.ruleEvaluator = ruleEvaluator;
        this.topicService = topicService;
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

        Set<Subscriber> subscribers = subscribers(getMatchingTopics(publishRules, deltaFile), deltaFile, publisher.getName());

        if (subscribers.isEmpty()) {
            return handleNoMatches(publisher, deltaFile);
        }

        return subscribers;
    }

    private Set<Subscriber> subscribers(Set<String> topics, DeltaFile deltaFile, String publisherName) {
        // find all matching topics and map them to the set of subscribers
        Set<Subscriber> potentialSubscribers = topics.stream()
                .filter(topic -> topicAllowsDeltaFile(topic, deltaFile, publisherName))
                .map(topicService::getSubscribers)
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

    /**
     * Find the Topic with the given name and evaluate its filter
     * rules to determine if the DeltaFile can be sent to the topic
     * @param topicId id of the topic to attempt to publish to
     * @param deltaFile to evaluate against the topic filter rules
     * @return the Topic if DeltaFile can go there, otherwise null
     */
    boolean topicAllowsDeltaFile(String topicId, DeltaFile deltaFile, String publisherName) {
        Topic topic = topicService.getTopic(topicId).orElse(null);

        if (topic == null) {
            errorDeltaFile(deltaFile, ActionEventDetails.MISSING_TOPIC, publisherName, "Missing topic with an id of " + topicId);
            return false;
        }

        if (topicFiltersDeltaFile(topic, deltaFile)) {
            TopicFilterPolicy filterPolicy = Objects.requireNonNullElse(topic.getFilterPolicy(), TopicFilterPolicy.DROP);
            switch (filterPolicy) {
                case ERROR -> errorDeltaFile(deltaFile, ActionEventDetails.ERRORED_BY_TOPIC_FILTER, publisherName, topic.toString());
                case FILTER -> filterDeltaFile(deltaFile, ActionEventDetails.FILTERED_BY_TOPIC_FILTER, publisherName, topic.toString());
                case DROP -> log.trace("Topic {} ({}) dropped deltaFile {} because of the filter rules", topicId, topic.getName(), deltaFile.getDid());
            }
            return false;
        }

        return true;
    }

    private boolean subscriberMatches(Subscriber subscriber, DeltaFile deltaFile) {
        if (subscriber.subscriptions() == null) {
            return false;
        }

        return subscriber.subscriptions().stream()
                .anyMatch(rule -> ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFile));
    }

    private boolean topicFiltersDeltaFile(Topic topic, DeltaFile deltaFile) {
        if (topic.getFilters() == null) {
            return false;
        }

        return topic.getFilters().stream()
                .anyMatch(filter -> ruleEvaluator.evaluateCondition(filter, deltaFile));
    }

    private Set<Subscriber> handleNoMatches(Publisher publisher, DeltaFile deltaFile) {
        PublishRules publishRules = publisher.publishRules();
        String publisherName = publisher.getName();
        DefaultRule defaultRule = Objects.requireNonNullElse(publishRules.getDefaultRule(), ERROR_RULE);
        DefaultBehavior defaultBehavior = defaultRule.getDefaultBehavior();

        if (DefaultBehavior.PUBLISH.equals(defaultBehavior)) {
            Set<Subscriber> subscribers = subscribers(Set.of(defaultRule.getTopic()), deltaFile, publisherName);
            if (!subscribers.isEmpty()) {
                return subscribers;
            }

            // Fall back to error mode, the topic either didn't exist, filtered the DeltaFile or did not have subscribers that accepted the DeltaFile
            defaultBehavior = DefaultBehavior.ERROR;
        }

        String context = "No subscribers found using publisher `" + publisher.getName() + "`\n" + publishRules;
        if (DefaultBehavior.FILTER.equals(defaultBehavior)) {
            filterDeltaFile(deltaFile, ActionEventDetails.NO_SUBSCRIBER, publisherName, context);
        } else {
            errorDeltaFile(deltaFile, ActionEventDetails.NO_SUBSCRIBER, publisherName, context);
        }

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }

        return Set.of();
    }

    private boolean evaluateRule(Rule rule, DeltaFile deltaFile) {
        return ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFile);
    }

    private void errorDeltaFile(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName, String context) {
        queueSyntheticAction(deltaFile, actionEventDetails, publisherName);
        deltaFile.errorAction(buildErrorEvent(deltaFile, actionEventDetails, publisherName, context));
    }

    private void filterDeltaFile(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName, String context) {
        queueSyntheticAction(deltaFile, actionEventDetails, publisherName);
        deltaFile.filterAction(buildFilterEvent(deltaFile, actionEventDetails, publisherName, context));
    }

    private void queueSyntheticAction(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName) {
        deltaFile.queueNewAction(publisherName, actionEventDetails.eventName, ActionType.PUBLISH, false);
    }

    private ActionEvent buildFilterEvent(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName, String context) {
        return buildActionEvent(deltaFile, actionEventDetails, publisherName)
                .filter(FilterEvent.builder().message(actionEventDetails.cause).context(context).build())
                .build();
    }

    private ActionEvent buildErrorEvent(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName, String context) {
        return buildActionEvent(deltaFile, actionEventDetails, publisherName)
                .error(ErrorEvent.builder().cause(actionEventDetails.cause).context(context).build())
                .build();
    }

    private ActionEvent.ActionEventBuilder buildActionEvent(DeltaFile deltaFile, ActionEventDetails actionEventDetails, String publisherName) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(publisherName)
                .action(actionEventDetails.eventName)
                .start(now)
                .stop(now)
                .type(ActionEventType.PUBLISH);
    }

    enum ActionEventDetails {
        ERRORED_BY_TOPIC_FILTER("ERRORED_BY_TOPIC_FILTER", "Errored by topic filter rules"),
        FILTERED_BY_TOPIC_FILTER("FILTERED_BY_TOPIC_FILTER", "Filtered by topic filter rules"),
        MISSING_TOPIC("MISSING_TOPIC", "Attempted to publish to a topic that was not found"),
        NO_SUBSCRIBER("NO_SUBSCRIBERS", "No matching subscribers were found");

        final String eventName;
        final String cause;

        ActionEventDetails(String eventName, String cause) {
            this.eventName = eventName;
            this.cause = cause;
        }
    }
}
