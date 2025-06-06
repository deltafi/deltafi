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
package org.deltafi.core.services.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.types.*;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The PublisherService is used to find the next destinations for
 * a DeltaFile after it has been processed by a publisher
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherService {

    private static final DefaultRule ERROR_RULE = new DefaultRule(DefaultBehavior.ERROR);
    public static final String NO_SUBSCRIBERS = "NO_SUBSCRIBERS";
    public static final String NO_SUBSCRIBER_CAUSE = "No matching subscribers were found";

    private final RuleEvaluator ruleEvaluator;
    private final List<SubscriberService> subscriberServices;
    private final Clock clock;
    private final AnalyticEventService analyticEventService;
    private final FlowDefinitionService flowDefinitionService;

    /**
     * Create new DeltaFileFlows for each subscriber that accepts the Deltafile and return the set.
     * Adds synthetic errors or filter actions based publisher and subscriber rules
     * that are applied. If there are no matching subscribers and no pending actions
     * the DeltaFile will be moved to a terminal state.
     * @param flow dataSource used to determine what topics to send the DeltaFile to
     * @param deltaFile that will be sent to the matching topics
     * @param publishingFlow DeltaFileFlow that was completed and now publishing the DeltaFile
     * @return DeltaFileFlows that were added to the DeltaFile based on the matching subscribers
     */
    public Set<DeltaFileFlow> subscribers(Flow flow, DeltaFile deltaFile, DeltaFileFlow publishingFlow) {
        Objects.requireNonNull(flow, "The dataSource cannot be null");
        if (flow instanceof DataSource dataSource) {
            return dataSourceSubscribers(dataSource, deltaFile, publishingFlow);
        } else if (flow instanceof Publisher publisher) {
            return publisherSubscribers(publisher, deltaFile, publishingFlow);
        } else {
            throw new IllegalArgumentException("Unexpected type " + flow.getClass().getSimpleName());
        }
    }


    Set<DeltaFileFlow> publisherSubscribers(Publisher publisher, DeltaFile deltaFile, DeltaFileFlow publishingFlow) {
        PublishRules publishRules = publisher.publishRules();
        if (publishRules == null) {
            errorDeltaFile(deltaFile, publishingFlow, "Flow " + publisher.getName() + " does not have publish rules");
            return Collections.emptySet();
        }

        Set<String> publishTopics = getMatchingTopics(publishRules, publishingFlow);
        publishingFlow.setPublishTopics(new ArrayList<>(publishTopics));
        Set<DeltaFileFlow> subscribers = subscribers(publishTopics, deltaFile, publishingFlow);

        if (subscribers.isEmpty()) {
            return handleNoMatches(publisher, deltaFile, publishingFlow, publishTopics);
        }

        return subscribers;
    }

    Set<DeltaFileFlow> dataSourceSubscribers(DataSource dataSource, DeltaFile deltaFile, DeltaFileFlow publishingFlow) {
        publishingFlow.setPublishTopics(List.of(dataSource.getTopic()));
        Set<DeltaFileFlow> subscribers = subscribers(Set.of(dataSource.getTopic()), deltaFile, publishingFlow);

        if (subscribers.isEmpty()) {
            handleNoMatches(deltaFile, dataSource, publishingFlow);
        }

        return subscribers;
    }

    /**
     * Add a new DeltaFileFlow to the DeltaFile and return it
     * @param subscriber to create the DeltaFileFlow from
     * @param deltaFile to add the DeltaFileFlow to
     * @param previousFlow used as the input DeltaFileFlow for the new DeltaFileFlow
     * @return DeltaFileFlow that is added to the DeltaFile
     */
    private DeltaFileFlow deltaFileFlow(Subscriber subscriber, DeltaFile deltaFile, DeltaFileFlow previousFlow, Set<String> sourceTopics) {
        DeltaFileFlow nextFlow = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(subscriber.getName(), subscriber.flowType()),
                previousFlow, sourceTopics, OffsetDateTime.now(clock));
        nextFlow.setPendingActions(subscriber.allActionConfigurations().stream().map(ActionConfiguration::getName).toList());
        if (subscriber.isTestMode()) {
            nextFlow.setTestMode(true);
            nextFlow.setTestModeReason(subscriber.getName());
        }
        if (subscriber.isPaused()) {
            nextFlow.setState(DeltaFileFlowState.PAUSED);
        }
        return nextFlow;
    }

    private Set<DeltaFileFlow> subscribers(Set<String> topics, DeltaFile deltaFile, DeltaFileFlow completedFlow) {
        return topics.stream()
                .flatMap(topic -> getSubscribers(topic).stream())
                .distinct()
                .map(subscriber -> subscribedDeltaFileFlow(subscriber, deltaFile, completedFlow, topics))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Evaluate the PublishRules to find the matching topics.
     * If no matching mode is present {@link MatchingPolicy#ALL_MATCHING} is used
     * @param publishRules contains the rules and policies used to find the matching topics
     * @param deltaFileFlow to match against the PublishRules
     * @return set of the matching topics
     */
    Set<String> getMatchingTopics(PublishRules publishRules, DeltaFileFlow deltaFileFlow) {
        if (publishRules == null || publishRules.getRules() == null) {
            return Set.of();
        }

        if (MatchingPolicy.FIRST_MATCHING.equals(publishRules.getMatchingPolicy())) {
            return publishRules.getRules().stream()
                    .filter(rule -> evaluateRule(rule, deltaFileFlow))
                    .findFirst()
                    .map(r -> Set.of(r.getTopic()))
                    .orElse(Set.of());
        }

        return publishRules.getRules().stream()
                .filter(rule -> evaluateRule(rule, deltaFileFlow))
                .map(Rule::getTopic)
                .collect(Collectors.toSet());
    }

    /**
     * Get the set of topics that match for the subscriber. If there are matches, convert add a new DeltaFileFlow
     * to the DeltaFile and return it. Otherwise, return null.
     * @param subscriber whose subscribeRules will be matched against the completeDeltaFileFlow
     * @param deltaFile to add the new DeltaFileFlow to
     * @param completeDeltaFileFlow completed DeltaFileFlow that is publishing the DeltaFile
     * @param topics that the DeltaFile is published on
     * @return new DeltaFileFlow if the subscriber matches otherwise null
     */
    private DeltaFileFlow subscribedDeltaFileFlow(Subscriber subscriber, DeltaFile deltaFile, DeltaFileFlow completeDeltaFileFlow, Set<String> topics) {
        Set<String> matchedTopics = subscriber.subscribeRules().stream()
                .map(subscription -> matchingTopic(subscription, completeDeltaFileFlow, topics))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return matchedTopics.isEmpty() ? null : deltaFileFlow(subscriber, deltaFile, completeDeltaFileFlow, matchedTopics);
    }

    private String matchingTopic(Rule rule, DeltaFileFlow deltaFileFlow, Set<String> topics) {
        if (topics.contains(rule.getTopic()) && ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFileFlow.getMetadata(), deltaFileFlow.lastContent().stream().map(c -> new Content(c.getName(), c.getMediaType(), c.getSegments())).toList())) {
            return rule.getTopic();
        }

        return null;
    }

    private Set<DeltaFileFlow> handleNoMatches(Publisher publisher, DeltaFile deltaFile, DeltaFileFlow completedFlow, Set<String> publishTopics) {
        PublishRules publishRules = publisher.publishRules();
        DefaultRule defaultRule = Objects.requireNonNullElse(publishRules.getDefaultRule(), ERROR_RULE);
        DefaultBehavior defaultBehavior = defaultRule.getDefaultBehavior();

        if (DefaultBehavior.PUBLISH.equals(defaultBehavior)) {
            Set<DeltaFileFlow> subscribers = subscribers(Set.of(defaultRule.getTopic()), deltaFile, completedFlow);
            if (!subscribers.isEmpty()) {
                return subscribers;
            }

            // Fall back to error mode, the topic either didn't exist, filtered the DeltaFile or did not have subscribers that accepted the DeltaFile
            defaultBehavior = DefaultBehavior.ERROR;
        }

        String context = "No subscribers found from " + completedFlow.getType().getDisplayName() + " '" + publisher.getName() + "' ";
        if (publishTopics.isEmpty()) {
            context += "because no topics matched the criteria.";
        } else {
            context += "listening on matching topics: " + String.join(", ", publishTopics);
        }
        context += "\nWith rules:\n" + publishRules;

        if (DefaultBehavior.FILTER.equals(defaultBehavior)) {
            filterDeltaFile(deltaFile, completedFlow, context);
        } else {
            errorDeltaFile(deltaFile, completedFlow, context);
        }

        return Set.of();
    }

    private void handleNoMatches(DeltaFile deltaFile, DataSource dataSource, DeltaFileFlow flow) {
        errorDeltaFile(deltaFile, flow, "No subscribers found for " + dataSource.getType().getDisplayName() + " '" + dataSource.getName() + "'" + " on topic '" + dataSource.getTopic() + "'");
    }

    private Set<Subscriber> getSubscribers(String topic) {
        return subscriberServices.stream()
                .map(subscriberService -> subscriberService.subscriberForTopic(topic))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private boolean evaluateRule(Rule rule, DeltaFileFlow deltaFileFlow) {
        return ruleEvaluator.evaluateCondition(rule.getCondition(), deltaFileFlow.getMetadata(), deltaFileFlow.lastContent().stream().map(c -> new Content(c.getName(), c.getMediaType(), c.getSegments())).toList());
    }

    private void errorDeltaFile(DeltaFile deltaFile, DeltaFileFlow flow, String context) {
        // grab the last content list to copy into the synthetic error action to make it available for retry
        List<Content> toCopy =  flow.lastActionContent();
        Action action = queueSyntheticAction(flow);
        action.setState(ActionState.ERROR);
        action.setErrorCause(NO_SUBSCRIBER_CAUSE);
        action.setErrorContext(context);
        action.setContent(toCopy);
        flow.updateState();
        deltaFile.updateState(OffsetDateTime.now(clock));
        analyticEventService.recordError(deltaFile, flow.getName(), flow.getType(), action.getName(), NO_SUBSCRIBER_CAUSE, action.getModified());
    }

    private void filterDeltaFile(DeltaFile deltaFile, DeltaFileFlow flow, String context) {
        Action action = queueSyntheticAction(flow);
        action.setState(ActionState.FILTERED);
        action.setFilteredCause(NO_SUBSCRIBER_CAUSE);
        action.setFilteredContext(context);
        flow.updateState();
        deltaFile.updateState(OffsetDateTime.now(clock));
        analyticEventService.recordFilter(deltaFile, flow.getName(), flow.getType(), action.getName(), NO_SUBSCRIBER_CAUSE, action.getModified());
    }

    private Action queueSyntheticAction(DeltaFileFlow flow) {
        return flow.queueNewAction(NO_SUBSCRIBERS, null, ActionType.PUBLISH, false, OffsetDateTime.now(clock));
    }
}
