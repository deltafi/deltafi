/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

const getAllTopicsQuery = {
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
const topicNames: Ref<Array<String | undefined>> = ref([]);

export default function useTopics() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();

  const hasActiveSubscribers = async (topicName: string) => {
    if (topics.value.length === 0) await getAllTopics();

    const topic = _.find(topics.value, (topic) => topic.name === topicName);

    return topic ? _.some(topic.subscribers, { 'state': 'RUNNING' }) : false
  };

  const getAllTopics = async () => {
    await queryGraphQL(getAllTopicsQuery, "getAllTopics");

    topics.value = response.value.data.getAllTopics;
    topicNames.value = _.map(topics.value, "name");

    return topics.value;
  };

  const getAllTopicNames = async () => {
    await getAllTopics();

    return topicNames.value;
  };

  const getTopic = async (topicName: string) => {
    if (topics.value.length === 0) await getAllTopics();

    const topic = _.find(topics.value, (topic) => topic.name === topicName);

    if (!topic) {
      throw new Error(`Topic ${topicName} not found`);
    }

    return topic;
  }

  return { topics: readonly(topics), topicNames: readonly(topicNames), response, hasActiveSubscribers, getTopic, getAllTopics, getAllTopicNames, loading, loaded, errors };
}
