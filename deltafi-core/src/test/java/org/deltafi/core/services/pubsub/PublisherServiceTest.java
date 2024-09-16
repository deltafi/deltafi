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
package org.deltafi.core.services.pubsub;

import org.assertj.core.api.Assertions;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.core.types.Action;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DefaultBehavior;
import org.deltafi.common.types.DefaultRule;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.common.types.MatchingPolicy;
import org.deltafi.common.types.PublishRules;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.types.TransformFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @InjectMocks
    PublisherService publisherService;

    @Spy
    @SuppressWarnings("unused")
    Clock clock = new TestClock();

    SubscriberService mockSubscriberService = Mockito.mock(SubscriberService.class);
    @Spy @SuppressWarnings("unused")
    List<SubscriberService> subscriberServices = List.of(mockSubscriberService);

    @Mock
    RuleEvaluator ruleEvaluator;

    @Test
    void subscribers() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        String condition = "metadata != null";
        Subscriber subscriber = flow(null, Set.of(rule("topic", condition)));

        Mockito.when(mockSubscriberService.subscriberForTopic("topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(List.of(rule("topic", condition)));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);

        Assertions.assertThat(subscribers).hasSize(1);
        DeltaFileFlow nextFlow = subscribers.iterator().next();
        Assertions.assertThat(nextFlow.getName()).isEqualTo(subscriber.getName());
        Assertions.assertThat(nextFlow.getInput().getTopics()).isEqualTo(Set.of("topic"));
        Assertions.assertThat(nextFlow.getInput().getMetadata()).isEqualTo(deltaFileFlow.getMetadata());
        Assertions.assertThat(nextFlow.getInput().getContent()).isEqualTo(deltaFileFlow.lastContent());
        Assertions.assertThat(nextFlow.isTestMode()).isFalse();
    }

    @Test
    void testModeFromSubscribers() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        String condition = "metadata != null";
        TransformFlow subscriber = flow(null, Set.of(rule("topic", condition)));
        subscriber.setName("transformFlow");
        subscriber.setTestMode(true);

        Mockito.when(mockSubscriberService.subscriberForTopic("topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(List.of(rule("topic", condition)));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);
        Assertions.assertThat(subscribers).hasSize(1);
        DeltaFileFlow nextFlow = subscribers.iterator().next();
        Assertions.assertThat(nextFlow.getName()).isEqualTo(subscriber.getName());
        Assertions.assertThat(nextFlow.getInput().getTopics()).isEqualTo(Set.of("topic"));
        Assertions.assertThat(nextFlow.getInput().getMetadata()).isEqualTo(deltaFileFlow.getMetadata());
        Assertions.assertThat(nextFlow.getInput().getContent()).isEqualTo(deltaFileFlow.lastContent());
        Assertions.assertThat(nextFlow.isTestMode()).isTrue();
        Assertions.assertThat(nextFlow.getTestModeReason()).isEqualTo("transformFlow");
    }

    @Test
    void testModeCarriedToSubscribers() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        deltaFileFlow.setTestMode(true);
        deltaFileFlow.setTestModeReason("data source test mode enabled");

        String condition = "metadata != null";
        Subscriber subscriber = flow(null, Set.of(rule("topic", condition)));

        Mockito.when(mockSubscriberService.subscriberForTopic("topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(List.of(rule("topic", condition)));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);
        Assertions.assertThat(subscribers).hasSize(1);
        DeltaFileFlow nextFlow = subscribers.iterator().next();
        Assertions.assertThat(nextFlow.getName()).isEqualTo(subscriber.getName());
        Assertions.assertThat(nextFlow.getInput().getTopics()).isEqualTo(Set.of("topic"));
        Assertions.assertThat(nextFlow.getInput().getMetadata()).isEqualTo(deltaFileFlow.getMetadata());
        Assertions.assertThat(nextFlow.getInput().getContent()).isEqualTo(deltaFileFlow.lastContent());
        Assertions.assertThat(nextFlow.isTestMode()).isTrue();
        Assertions.assertThat(nextFlow.getTestModeReason()).isEqualTo("data source test mode enabled");
    }

    @Test
    void subscribers_defaultToError() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        PublishRules publishRules = new PublishRules();
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFileFlow.getActions()).hasSize(1);
        Action action = deltaFileFlow.getActions().getFirst();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void subscribers_defaultToFilter() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.FILTER));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFile.getFiltered()).isTrue();
        Assertions.assertThat(deltaFileFlow.getActions()).hasSize(1);
        Action action = deltaFileFlow.getActions().getFirst();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.FILTERED);
        Assertions.assertThat(action.getErrorCause()).isNull();
        Assertions.assertThat(action.getErrorContext()).isNull();
        Assertions.assertThat(action.getFilteredCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
    }

    @Test
    void subscribers_defaultToPublish() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        Subscriber subscriber = flow(null, Set.of(rule("default-topic", null)));
        Mockito.when(mockSubscriberService.subscriberForTopic("default-topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(null, deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);

        Assertions.assertThat(subscribers).hasSize(1);

        DeltaFileFlow nextFlow = subscribers.iterator().next();
        Assertions.assertThat(nextFlow).isNotNull();
    }

    @Test
    void subscribers_defaultPublishFails() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = flow(publishRules, Set.of());

        Set<DeltaFileFlow> subscribers = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);
        Assertions.assertThat(subscribers).isEmpty();
        Assertions.assertThat(deltaFileFlow.getActions()).hasSize(1);
        Action action = deltaFileFlow.getActions().getFirst();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void getMatchingTopicNames() {
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        Mockito.when(ruleEvaluator.evaluateCondition("a", deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(false);
        Mockito.when(ruleEvaluator.evaluateCondition("b", deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);
        Mockito.when(ruleEvaluator.evaluateCondition("c", deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(new ArrayList<>());
        publishRules.getRules().add(rule("a", "a"));
        publishRules.getRules().add(rule("b", "b"));
        publishRules.getRules().add(rule("c", "c"));

        // defaults to ALL_MATCHING when the MatchMode is null
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFileFlow)).isEqualTo(Set.of("b", "c"));

        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFileFlow)).isEqualTo(Set.of("b"));

        publishRules.setMatchingPolicy(MatchingPolicy.ALL_MATCHING);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFileFlow)).isEqualTo(Set.of("b", "c"));

        // null set of rules should always return an empty set of subscribers
        publishRules.setRules(null);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFileFlow)).isEmpty();
    }

    /**
     * Test to verify the correct subset of subscription rules are evaluated
     * for a subscriber (i.e. if  a DeltaFile is read off a topic `a` only
     * subscriptions rules that include topic `a` should be evaluated)
     */
    @Test
    void testCorrectTopicUsed() {
        PublishRules publishRules = new PublishRules();
        publishRules.setRules(new ArrayList<>());
        publishRules.getRules().add(rule("a", "publish-a"));

        TransformFlow publisher = new TransformFlow();
        publisher.setPublish(publishRules);
        TransformFlow subscriber = new TransformFlow();

        Rule subscriptionA = rule("a", "subscribe-a");
        Rule subscriptionB = rule("b", "subscribe-b");
        subscriber.setSubscribe(Set.of(subscriptionA, subscriptionB));

        DeltaFile deltaFile = new DeltaFile();
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        deltaFile.setFlows(List.of(deltaFileFlow));

        mockRuleEval("publish-a", deltaFileFlow, true);
        mockRuleEval("subscribe-a", deltaFileFlow, false);
        Mockito.when(mockSubscriberService.subscriberForTopic("a")).thenReturn(Set.of(subscriber));

        Assertions.assertThat(publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow)).isEmpty();

        // the second subscription should not be evaluated because the DeltaFile was only published to topic `a`
        Mockito.verify(ruleEvaluator, Mockito.never()).evaluateCondition("subscribe-b", deltaFileFlow.getMetadata(), deltaFileFlow.lastContent());
    }

    @Test
    void testSetInputTopics() {
        TransformFlow publisher = new TransformFlow();
        PublishRules publishRules = new PublishRules();
        publishRules.setMatchingPolicy(MatchingPolicy.ALL_MATCHING);
        Rule publish = new Rule("a", null);
        Rule publish2 = new Rule("b", null);
        Rule publish3 = new Rule("c", null);
        Rule publish4 = new Rule("e", null);
        Rule publish5 = new Rule("h", null);
        publishRules.setRules(List.of(publish, publish2, publish3, publish4, publish5));
        publisher.setPublish(publishRules);

        TransformFlow subscriber = new TransformFlow();
        Rule subscribe = new Rule("b", null);
        Rule subscribe2 = new Rule("c", null);
        Rule subscribe3 = new Rule("d", null);
        Rule subscribe4 = new Rule("e", null);
        Rule subscribe5 = new Rule("f", null);
        Rule subscribe6 = new Rule("g", null);
        subscriber.setSubscribe(Set.of(subscribe, subscribe2, subscribe3, subscribe4, subscribe5, subscribe6));

        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();

        mockRuleEval(null, deltaFileFlow, true);

        Mockito.when(mockSubscriberService.subscriberForTopic("a")).thenReturn(Set.of(subscriber));

        Set<DeltaFileFlow> flows = publisherService.publisherSubscribers(publisher, deltaFile, deltaFileFlow);
        Assertions.assertThat(flows).hasSize(1);
        DeltaFileFlow nextFlow = flows.iterator().next();
        Assertions.assertThat(nextFlow.getInput().getTopics()).isEqualTo(Set.of("b", "c", "e"));
        Assertions.assertThat(nextFlow).isEqualTo(deltaFile.getFlows().get(1));
    }

    void  mockRuleEval(String condition, DeltaFileFlow deltaFileFlow, boolean result) {
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFileFlow.getMetadata(), deltaFileFlow.lastContent())).thenReturn(result);
    }

    private Rule rule(String topic, String condition) {
        return new Rule(topic, condition);
    }

    private DeltaFile deltaFile() {
        DeltaFile deltaFile = new DeltaFile();
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        deltaFile.setFlows(new ArrayList<>(List.of(deltaFileFlow)));
        return deltaFile;
    }

    private TransformFlow flow(PublishRules publishRules, Set<Rule> subscriptions) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setPublish(publishRules);
        transformFlow.setSubscribe(subscriptions);
        return transformFlow;
    }
}