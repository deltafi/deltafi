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

import org.assertj.core.api.Assertions;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.Action;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DefaultBehavior;
import org.deltafi.common.types.DefaultRule;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.MatchingPolicy;
import org.deltafi.common.types.PublishRules;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.HashSet;
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
        DeltaFile deltaFile = new DeltaFile();
        String condition = "metadata != null";
        Subscriber subscriber = new TestFlow(null, Set.of(rule("topic", condition)));

        Mockito.when(mockSubscriberService.subscriberForTopic("topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(Set.of(rule("topic", condition)));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);

        Assertions.assertThat(subscribers).isEqualTo(Set.of(subscriber));
    }

    @Test
    void subscribers_defaultToError() {
        DeltaFile deltaFile = new DeltaFile();

        PublishRules publishRules = new PublishRules();
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void subscribers_defaultToFilter() {
        DeltaFile deltaFile = new DeltaFile();

        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.FILTER));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFile.getFiltered()).isTrue();
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.FILTERED);
        Assertions.assertThat(action.getErrorCause()).isNull();
        Assertions.assertThat(action.getErrorContext()).isNull();
        Assertions.assertThat(action.getFilteredCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
    }

    @Test
    void subscribers_defaultToPublish() {
        DeltaFile deltaFile = new DeltaFile();
        Subscriber subscriber = new TestFlow(null, Set.of(rule("topic", null)));
        Mockito.when(mockSubscriberService.subscriberForTopic("default-topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(null, deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);
        Assertions.assertThat(subscribers).containsExactly(subscriber);
    }

    @Test
    void subscribers_defaultPublishFails() {
        DeltaFile deltaFile = new DeltaFile();
        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);
        Assertions.assertThat(subscribers).isEmpty();
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo(PublisherService.NO_SUBSCRIBER_CAUSE);
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void getMatchingTopicNames() {
        DeltaFile deltaFile = new DeltaFile();
        Mockito.when(ruleEvaluator.evaluateCondition("a", deltaFile)).thenReturn(false);
        Mockito.when(ruleEvaluator.evaluateCondition("b", deltaFile)).thenReturn(true);
        Mockito.when(ruleEvaluator.evaluateCondition("c", deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setRules(new HashSet<>());
        publishRules.getRules().add(rule("a", "a"));
        publishRules.getRules().add(rule("b", "b"));
        publishRules.getRules().add(rule("c", "c"));

        // defaults to ALL_MATCHING when the MatchMode is null
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b", "c"));

        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b"));

        publishRules.setMatchingPolicy(MatchingPolicy.ALL_MATCHING);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b", "c"));

        // null set of rules should always return an empty set of subscribers
        publishRules.setRules(null);
        Assertions.assertThat(publisherService.getMatchingTopics(publishRules, deltaFile)).isEmpty();
    }

    private Rule rule(String topic, String condition) {
        return new Rule(Set.of(topic), condition);
    }

    public record TestFlow(PublishRules publishRules, Set<Rule> subscriptions) implements Publisher, Subscriber {
        public String getName() { return "flow";}
    }
}