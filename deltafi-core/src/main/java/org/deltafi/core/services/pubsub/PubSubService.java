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
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.ErrorEvent;
import org.deltafi.common.types.FilterEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The PubSubService is used to find the next destinations for
 * a DeltaFile after it has been processed by a publisher
 */
@Slf4j
@Service
public class PubSubService {

    private static final DefaultRule ERROR_RULE = new DefaultRule(DefaultBehavior.ERROR);
    private static final String NO_DESTINATIONS_WERE_FOUND = "No destinations were found";
    private static final String NO_SUBSCRIBERS_ACTION = "NO_SUBSCRIBER_ACTION";

    private final RuleEvaluator ruleEvaluator;
    private final TopicService topicService;
    private final Clock clock;

    public PubSubService(RuleEvaluator ruleEvaluator, TopicService topicService, Clock clock) {
        this.ruleEvaluator = ruleEvaluator;
        this.topicService = topicService;
        this.clock = clock;
    }

    /**
     * Find the subscribers that should receive the DeltaFile.
     * @param publisher publisher used to determine the DeltaFiles subscribers
     * @param deltaFile that will be sent to the matching subscribers
     * @return subscribers that should receive the DeltaFile next
     */
    public Set<Subscriber> subscribers(Publisher publisher, DeltaFile deltaFile) {
        Objects.requireNonNull(publisher, "The publisher cannot be null");
        PublishRules publishRules = publisher.publishRules();
        Objects.requireNonNull(publishRules, "The publish rules cannot be null");

        Set<Subscriber> subscribers = subscribers(getMatchingTopics(publishRules, deltaFile), deltaFile, publishRules.getPublisherName());

        if (subscribers.isEmpty()) {
            return handleNoMatches(publishRules, deltaFile);
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
                    .map(rule -> Set.of(rule.topic())).orElse(Set.of());
        }

        return publishRules.getRules().stream()
                .filter(rule -> evaluateRule(rule, deltaFile))
                .map(Rule::topic)
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
            errorDeltaFile(deltaFile, publisherName, "Missing topic", "Missing topic with an id of " + topicId);
            return false;
        }

        if (topicFiltersDeltaFile(topic, deltaFile)) {
            TopicFilterPolicy filterPolicy = Objects.requireNonNullElse(topic.getFilterPolicy(), TopicFilterPolicy.DROP);
            switch (filterPolicy) {
                case ERROR -> errorDeltaFile(deltaFile, publisherName, "Errored by topic filter rules", topic.toString());
                case FILTER -> filterDeltaFile(deltaFile, publisherName, "Filtered by topic filter rules");
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
                .anyMatch(rule -> ruleEvaluator.evaluateCondition(rule.condition(), deltaFile));
    }

    private boolean topicFiltersDeltaFile(Topic topic, DeltaFile deltaFile) {
        if (topic.getFilters() == null) {
            return false;
        }

        return topic.getFilters().stream()
                .anyMatch(filter -> ruleEvaluator.evaluateCondition(filter, deltaFile));
    }

    private Set<Subscriber> handleNoMatches(PublishRules publishRules, DeltaFile deltaFile) {
        String publisherName = publishRules.getPublisherName();
        DefaultRule defaultRule = Objects.requireNonNullElse(publishRules.getDefaultRule(), ERROR_RULE);
        DefaultBehavior defaultBehavior = defaultRule.defaultBehavior();

        if (DefaultBehavior.PUBLISH.equals(defaultBehavior)) {
            Set<Subscriber> subscribers = subscribers(Set.of(defaultRule.topic()), deltaFile, publisherName);
            if (!subscribers.isEmpty()) {
                return subscribers;
            }

            // Fall back to error mode, the topic either didn't exist, filtered the DeltaFile or did not have subscribers that accepted the DeltaFile
            defaultBehavior = DefaultBehavior.ERROR;
        }

        if (DefaultBehavior.FILTER.equals(defaultBehavior)) {
            filterDeltaFile(deltaFile, publisherName, NO_DESTINATIONS_WERE_FOUND);
        } else {
            errorDeltaFile(deltaFile, publisherName, NO_DESTINATIONS_WERE_FOUND, "No subscribers found using publish rules: \n" + publishRules);
        }

        return Set.of();
    }

    private boolean evaluateRule(Rule rule, DeltaFile deltaFile) {
        return ruleEvaluator.evaluateCondition(rule.condition(), deltaFile);
    }

    private void errorDeltaFile(DeltaFile deltaFile, String publisherName, String cause, String context) {
        queueSyntheticAction(deltaFile, publisherName);
        deltaFile.errorAction(buildErrorEvent(deltaFile, publisherName, cause, context));
    }

    private void filterDeltaFile(DeltaFile deltaFile, String publisherName, String reason) {
        queueSyntheticAction(deltaFile, publisherName);
        deltaFile.filterAction(buildFilterEvent(deltaFile, publisherName, reason));
    }

    private void queueSyntheticAction(DeltaFile deltaFile, String publisherName) {
        deltaFile.queueNewAction(publisherName, NO_SUBSCRIBERS_ACTION, ActionType.UNKNOWN, false);
    }

    private ActionEvent buildFilterEvent(DeltaFile deltaFile, String publisherName, String message) {
        return buildActionEvent(deltaFile, publisherName)
                .filter(FilterEvent.builder().message(message).build())
                .build();
    }

    private ActionEvent buildErrorEvent(DeltaFile deltaFile, String publisherName, String cause, String context) {
        return buildActionEvent(deltaFile, publisherName)
                .error(ErrorEvent.builder().cause(cause).context(context).build())
                .build();
    }

    private ActionEvent.ActionEventBuilder buildActionEvent(DeltaFile deltaFile, String publisherName) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(publisherName)
                .action(NO_SUBSCRIBERS_ACTION)
                .start(now)
                .stop(now)
                .type(ActionEventType.UNKNOWN);
    }
}
