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

import useGraphQL from "./useGraphQL";

import _ from "lodash";

export default function useTopics() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();

  const getRunningFlowsQuery = {
    getRunningFlows: {
      transform: {
        subscribe: {
          topic: true,
        },
      },
    },
  };

  const hasActiveSubscribers = async (topic: string) => {
    await queryGraphQL(getRunningFlowsQuery, "getRunningFlows");
    for (const flow of response.value.data.getRunningFlows.transform) {
      for (const sub of flow.subscribe) {
        if (sub.topic === topic) {
          return true;
        }
      }
    }
    return false;
  };

  const getAllTopicsQuery = {
    getAllFlows: {
      egress: {
        subscribe: {
          topic: true,
        },
      },
      dataSource: {
        topic: true,
      },
      transform: {
        subscribe: {
          topic: true,
        },
        publish: {
          rules: {
            topic: true,
          },
        },
      },
    },
  };

  const getAllTopics = async () => {
    await queryGraphQL(getAllTopicsQuery, "getAllTopicsQuery");
    let topicsArray: any[] = [];

    // Gets egress topics
    const egressTopics: any[] = response.value.data.getAllFlows.egress?.flatMap((e: any) => e.subscribe?.map((s: any) => s.topic));
    topicsArray = topicsArray.concat(egressTopics);

    // Gets dataSource topics
    const dataSourceTopics: any[] = response.value.data.getAllFlows.dataSource?.map((e: any) => e.topic);
    topicsArray = topicsArray.concat(dataSourceTopics);

    // Gets transform subscribe topics
    const transformSubscribeTopics: any[] = response.value.data.getAllFlows.transform?.flatMap((e: any) => e.subscribe?.map((s: any) => s.topic));
    topicsArray = topicsArray.concat(transformSubscribeTopics);

    // Gets transform publish topics
    const transformPublishTopics: any[] = response.value.data.getAllFlows.transform.flatMap((e: any) => e.publish?.rules?.map((s: any) => s.topic));
    topicsArray = topicsArray.concat(transformPublishTopics);

    // Removes all falsey values from array
    topicsArray = _.compact(topicsArray);

    // Removes duplicates from array
    topicsArray = _.uniq(topicsArray);

    // Sorts array
    topicsArray = topicsArray.sort();

    return topicsArray;
  };

  return { response, hasActiveSubscribers, getAllTopics, loading, loaded, errors };
}
