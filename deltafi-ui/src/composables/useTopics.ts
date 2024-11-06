/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import { ref, Ref, readonly } from 'vue';
import useGraphQL from "./useGraphQL";
import _ from "lodash"

export type Topic = {
  name?: String,
  publishers?: Array<{
    name: String,
    type: String,
    state: String
    condition: String
  }>,
  subscribers?: Array<{
    name: String,
    type: String,
    state: String
    condition: String
  }>
};

const topics: Ref<Array<Topic>> = ref([]);

export default function useTopics() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();

  const hasActiveSubscribers = async (topicName: string) => {
    if (topics.value.length === 0) await getAllTopics();

    const topic = _.find(topics.value, (topic) => topic.name === topicName);

    return topic ? _.some(topic.subscribers, { 'state': 'RUNNING' }) : false
  };

  const getAllTopics = async () => {
    const query = {
      getAllTopics: {
        name: true,
        publishers: {
          name: true,
          type: true,
          state: true,
          condition: true
        },
        subscribers: {
          name: true,
          type: true,
          state: true,
          condition: true
        }
      },
    }
    await queryGraphQL(query, "getAllTopics");

    topics.value = response.value.data.getAllTopics;

    return topics.value;
  };

  const getAllTopicNames = async () => {
    await getAllTopics();

    return _.map(topics.value, "name");
  };

  return { topics: readonly(topics), response, hasActiveSubscribers, getAllTopics, getAllTopicNames, loading, loaded, errors };
}
