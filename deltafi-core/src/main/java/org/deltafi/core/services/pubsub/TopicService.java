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

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.Subscriber;
import org.deltafi.common.types.Topic;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.repo.TopicRepo;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TopicService {

    private final TopicRepo topicRepo;
    private final List<SubscriberService> subscriberServices;
    private final RuleValidator ruleValidator;
    private Map<String, Topic> topicMap = Map.of();

    public TopicService(TopicRepo topicRepo, List<SubscriberService> subscriberServices, RuleValidator ruleValidator) {
        this.topicRepo = topicRepo;
        this.subscriberServices = subscriberServices;
        this.ruleValidator = ruleValidator;
    }

    @PostConstruct
    public void setup() {
        refreshCache();
    }

    public synchronized void refreshCache() {
        topicMap = topicRepo.findAll().stream()
                .collect(Collectors.toMap(Topic::getName, Function.identity()));
    }

    /**
     * Find all subscribers that have subscriptions for the given topic
     * @param topicName whose subscribers should be found
     * @return subscribers that have subscriptions for the given topic
     */
    public Set<Subscriber> getSubscribers(String topicName) {
        return subscriberServices.stream()
                .map(subscriberService -> subscriberService.subscriberForTopic(topicName))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public Topic getTopicOrThrow(String topicName) {
        return getTopic(topicName).orElseThrow(() -> new DgsEntityNotFoundException("No topic with a name of " + topicName + " exists"));
    }

    public Optional<Topic> getTopic(String topicName) {
        return Optional.ofNullable(topicMap.get(topicName));
    }

    public List<Topic> getUncachedTopics() {
        return topicRepo.findAll();
    }

    public void saveTopics(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }

        topicRepo.saveAll(topics);
        refreshCache();
    }

    public Topic saveTopic(Topic topic) {
        List<String> errors = validateTopics(List.of(topic));
        if (!errors.isEmpty()) {
            throw new DeltafiConfigurationException("Invalid topics filter rules found: " + String.join("; ", errors));
        }

        Topic persisted = topicRepo.save(topic);
        refreshCache();
        return persisted;
    }

    public boolean deleteTopic(String topicId) {
        if (!getSubscribers(topicId).isEmpty()) {
            log.warn("Attempted to delete topic {} while there are subscribers", topicId);
            return false;
        }

        if (topicRepo.existsById(topicName)) {
            topicRepo.deleteById(topicName);
            return true;
        }

        log.warn("Attempted to delete a topic {} that does not exist in the repository", topicName);
        return false;
    }

    /**
     * Validate the topic filter rules if they are set
     * @param topics list of topics to validate
     * @return list of errors if the topics
     */
    public List<String> validateTopics(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return topics.stream().map(ruleValidator::validateTopic).flatMap(Collection::stream).toList();
    }
}
