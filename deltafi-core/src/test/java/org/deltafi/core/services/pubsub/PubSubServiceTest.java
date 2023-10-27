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
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.Action;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DeltaFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class PubSubServiceTest {

    @InjectMocks
    PubSubService pubSubService;

    @Spy
    @SuppressWarnings("unused")
    Clock clock = new TestClock();

    @Mock
    TopicService topicService;

    @Mock
    RuleEvaluator ruleEvaluator;

    @Test
    void subscribers() {
        DeltaFile deltaFile = new DeltaFile();
        String condition = "metadata != null";
        Topic topic = new Topic();
        topic.setName("topic");
        Subscriber subscriber = new TestFlow(null, Set.of(new Rule("topic", condition)));

        Mockito.when(topicService.getTopic("topic")).thenReturn(Optional.of(topic));
        Mockito.when(topicService.getSubscribers("topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(condition, deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setPublisherName("flow-name");
        publishRules.setRules(Set.of(new Rule("topic", condition)));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = pubSubService.subscribers(publisher, deltaFile);

        Assertions.assertThat(subscribers).isEqualTo(Set.of(subscriber));
    }

    @Test
    void subscribers_defaultToError() {
        DeltaFile deltaFile = new DeltaFile();

        PublishRules publishRules = new PublishRules();
        publishRules.setPublisherName("flow");
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = pubSubService.subscribers(publisher, deltaFile);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("No destinations were found");
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void subscribers_defaultToFilter() {
        DeltaFile deltaFile = new DeltaFile();

        PublishRules publishRules = new PublishRules();
        publishRules.setPublisherName("flow");
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.FILTER));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = pubSubService.subscribers(publisher, deltaFile);

        // null publish rules will result no matches
        Assertions.assertThat(subscribers).isEmpty();

        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.FILTERED);
        Assertions.assertThat(action.getErrorCause()).isNull();
        Assertions.assertThat(action.getErrorContext()).isNull();
        Assertions.assertThat(action.getFilteredCause()).isEqualTo("No destinations were found");
    }

    @Test
    void subscribers_defaultToPublish() {
        DeltaFile deltaFile = new DeltaFile();
        Topic topic = new Topic();
        topic.setName("default-topic");
        Subscriber subscriber = new TestFlow(null, Set.of(new Rule("topic", null)));

        Mockito.when(topicService.getTopic("default-topic")).thenReturn(Optional.of(topic));
        Mockito.when(topicService.getSubscribers("default-topic")).thenReturn(Set.of(subscriber));
        Mockito.when(ruleEvaluator.evaluateCondition(null, deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setPublisherName("flow");
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = pubSubService.subscribers(publisher, deltaFile);
        Assertions.assertThat(subscribers).containsExactly(subscriber);
    }

    @Test
    void subscribers_defaultPublishFails() {
        DeltaFile deltaFile = new DeltaFile();
        Topic topic = new Topic();
        topic.setName("default-topic");
        topic.getFilters().add("all");

        Mockito.when(topicService.getTopic("default-topic")).thenReturn(Optional.of(topic));
        Mockito.when(ruleEvaluator.evaluateCondition("all", deltaFile)).thenReturn(true);

        PublishRules publishRules = new PublishRules();
        publishRules.setPublisherName("flow");
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default-topic"));
        Publisher publisher = new TestFlow(publishRules, Set.of());

        Set<Subscriber> subscribers = pubSubService.subscribers(publisher, deltaFile);
        Assertions.assertThat(subscribers).isEmpty();
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("No destinations were found");
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
        publishRules.getRules().add(new Rule("a", "a"));
        publishRules.getRules().add(new Rule("b", "b"));
        publishRules.getRules().add(new Rule("c", "c"));

        // defaults to ALL_MATCHING when the MatchMode is null
        Assertions.assertThat(pubSubService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b", "c"));

        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        Assertions.assertThat(pubSubService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b"));

        publishRules.setMatchingPolicy(MatchingPolicy.ALL_MATCHING);
        Assertions.assertThat(pubSubService.getMatchingTopics(publishRules, deltaFile)).isEqualTo(Set.of("b", "c"));

        // null set of rules should always return an empty set of subscribers
        publishRules.setRules(null);
        Assertions.assertThat(pubSubService.getMatchingTopics(publishRules, deltaFile)).isEmpty();
    }


    @Test
    void topicAllowsDeltaFile() {
        DeltaFile deltaFile = new DeltaFile();
        Topic topic = new Topic();
        topic.setName("topic");
        topic.setFilters(Set.of("a", "b"));

        Mockito.when(ruleEvaluator.evaluateCondition(Mockito.anyString(), Mockito.eq(deltaFile)))
                        .thenAnswer(a -> a.getArgument(0).equals("b"));

        Mockito.when(ruleEvaluator.evaluateCondition("b", deltaFile)).thenReturn(true);
        Mockito.when(topicService.getTopic("topic")).thenReturn(Optional.of(topic));

        // by default the filter rules will drop the DeltaFile silently
        pubSubService.topicAllowsDeltaFile("topic", deltaFile, "flow");
        Assertions.assertThat(deltaFile.getActions()).isEmpty();

        // test adding a filter action to a DeltaFiles that is filtered by the topic
        topic.setFilterPolicy(TopicFilterPolicy.FILTER);
        pubSubService.topicAllowsDeltaFile("topic", deltaFile, "flow");
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.FILTERED);
        Assertions.assertThat(action.getFilteredCause()).isEqualTo("Filtered by topic filter rules");
        Assertions.assertThat(action.getErrorCause()).isNull();
        Assertions.assertThat(action.getErrorContext()).isNull();

        // test adding an error action to a DeltaFile that is filtered by the topic
        deltaFile.getActions().clear();
        topic.setFilterPolicy(TopicFilterPolicy.ERROR);
        pubSubService.topicAllowsDeltaFile("topic", deltaFile, "flow");
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("Errored by topic filter rules");
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    @Test
    void topicAllowsDeltaFile_missingTopic() {
        DeltaFile deltaFile = new DeltaFile();
        pubSubService.topicAllowsDeltaFile("topic", deltaFile, "flow");
        Assertions.assertThat(deltaFile.getActions()).hasSize(1);
        Action action = deltaFile.getActions().get(0);
        Assertions.assertThat(action.getFlow()).isEqualTo("flow");
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("Missing topic");
        Assertions.assertThat(action.getErrorContext()).isNotBlank();
        Assertions.assertThat(action.getFilteredCause()).isNull();
    }

    public record TestFlow(PublishRules publishRules, Set<Rule> subscriptions) implements Publisher, Subscriber {}
}