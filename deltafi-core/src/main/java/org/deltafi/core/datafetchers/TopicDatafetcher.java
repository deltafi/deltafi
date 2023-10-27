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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.pubsub.Topic;
import org.deltafi.core.services.pubsub.TopicService;

import java.util.List;

@DgsComponent
public class TopicDatafetcher {

    private final TopicService topicService;

    public TopicDatafetcher(TopicService topicService) {
        this.topicService = topicService;
    }

    @DgsQuery
    @NeedsPermission.TopicsRead
    public Topic getTopic(String topicId) {
        return topicService.getTopicOrThrow(topicId);
    }

    @DgsQuery
    @NeedsPermission.TopicsRead
    public Topic getTopicByName(String name) {
        return topicService.findTopicByName(name);
    }

    @DgsQuery
    @NeedsPermission.TopicsRead
    public List<Topic> getTopics() {
        return topicService.getUncachedTopics();
    }

    @DgsMutation
    @NeedsPermission.TopicsWrite
    public Topic saveTopic(Topic topic) {
        return topicService.saveTopic(topic);
    }

    @DgsMutation
    @NeedsPermission.TopicsDelete
    public boolean deleteTopic(String topicId) {
        return topicService.deleteTopic(topicId);
    }
}
