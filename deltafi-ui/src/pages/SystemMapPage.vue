<!--
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
-->

<template>
  <div class="system-map-page">
    <PageHeader>
      <template #header>
        <div class="align-items-center btn-group">
          <h2 class="mb-0">System Map</h2>
          <div class="ml-2">
            <Badge value="BETA" size="small" severity="info"></Badge>
          </div>
        </div>
      </template>
    </PageHeader>
    <ProgressBar v-if="!graphData" mode="indeterminate" style="height: 0.5em" />
    <template v-if="graphData">
      <div id="app">
        <CytoScape ref="test" :graph-data="graphData" />
      </div>
    </template>
  </div>
</template>

<script setup>
import CytoScape from "@/components/cytoscape/CytoScape.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useTopics from "@/composables/useTopics";
import { onMounted, ref } from "vue";

import Badge from "primevue/badge";

import _ from "lodash";

const { topics, getAllTopics } = useTopics();

const loading = ref(true);
const graphData = ref(null);

const formatGraphData = async () => {
  const topicsArray = _.map(topics.value, (topic) => {
    return {
      subscriberNames: _.map(topic.subscribers, "name"),
      publisherNames: _.map(topic.publishers, "name"),
      ...topic,
    };
  });

  const nodesArray = [];
  const edgeArray = [];

  let topicTransformY = 3738.56884765625;
  let topicNotTransformY = 3738.56884765625;
  let topicX = 5691.060546875;
  let topicY = 3738.56884765625;

  let transformY = 3738.56884765625;
  let dataSourceY = 3738.56884765625;
  let dataSinkY = 3738.56884765625;

  for (const topic of topicsArray) {
    if (_.includes(_.map(topic.publishers, "type"), "TRANSFORM")) {
      topicX = 6292.2421875;
      topicTransformY = topicTransformY + 60;
      topicY = topicTransformY;
    } else {
      topicX = 5691.060546875;
      topicNotTransformY = topicNotTransformY + 60;
      topicY = topicNotTransformY;
    }

    // Topic Node
    nodesArray.push({
      data: {
        id: _.snakeCase(topic.name),
        label: topic.name,
        name: topic.name,
        type: "TOPIC",
      },
      position: {
        x: topicX,
        y: topicY,
      },
      selected: false,
    });

    for (const publisher of topic.publishers) {
      const publisherNode = _.find(nodesArray, (obj) => _.get(obj, "data.id") === _.snakeCase(publisher.name + "-" + publisher.type));
      if (!publisherNode) {
        if (_.isEqual(publisher.type, "TRANSFORM")) {
          transformY = transformY + 60;
        } else {
          dataSourceY = dataSourceY + 60;
        }

        nodesArray.push({
          data: {
            id: _.snakeCase(publisher.name + "-" + publisher.type),
            label: publisher.name,
            selected: false,
            ...publisher,
          },
          position: {
            x: _.isEqual(publisher.type, "TRANSFORM") ? 5992.060546875 : 5391.108154296875,
            y: _.isEqual(publisher.type, "TRANSFORM") ? transformY : dataSourceY,
          },
          selected: false,
        });
      }

      edgeArray.push({
        data: {
          id: _.snakeCase(publisher.name + "-" + publisher.type + "-" + topic.name + "-" + "edge"),
          source: _.snakeCase(publisher.name + "-" + publisher.type),
          target: _.snakeCase(topic.name),
        },
        selected: false,
      });
    }

    for (const subscriber of topic.subscribers) {
      const subscriberNode = _.find(nodesArray, (obj) => _.get(obj, "data.id") === _.snakeCase(subscriber.name + "-" + subscriber.type));
      if (!subscriberNode) {
        if (_.isEqual(subscriber.type, "DATA_SINK")) {
          dataSinkY = dataSinkY + 60;
        } else {
          transformY = transformY + 60;
        }

        nodesArray.push({
          data: {
            id: _.snakeCase(subscriber.name + "-" + subscriber.type),
            label: subscriber.name,
            selected: false,
            ...subscriber,
          },
          position: {
            x: _.isEqual(subscriber.type, "DATA_SINK") ? 6593.2421875 : 5992.060546875,
            y: _.isEqual(subscriber.type, "DATA_SINK") ? dataSinkY : transformY,
          },
          selected: false,
        });
      }

      edgeArray.push({
        data: {
          id: _.snakeCase(topic.name + "-" + subscriber.name + "-" + subscriber.type + "-" + "edge"),
          source: _.snakeCase(topic.name),
          target: _.snakeCase(subscriber.name + "-" + subscriber.type),
        },
        selected: false,
      });
    }
  }
  const uniqueNodes = _.uniqWith(nodesArray, _.isEqual);
  const uniqueEdges = _.uniqWith(edgeArray, _.isEqual);

  const newObject = {
    nodes: uniqueNodes,
    edges: uniqueEdges,
  };

  newObject.nodes.forEach((n) => {
    const data = n.data;

    data.typeFormatted = data.type;

    // the source data for types isn't formatted well for reading
    if (data.typeFormatted === "REST_DATA_SOURCE") {
      data.typeFormatted = "Rest Data Source";
    } else if (data.typeFormatted === "TIMED_DATA_SOURCE") {
      data.typeFormatted = "Timed Data Source";
    } else if (data.typeFormatted === "ON_ERROR_DATA_SOURCE") {
      data.typeFormatted = "On-Error Data Source";
    } else if (data.typeFormatted === "TOPIC") {
      data.typeFormatted = "Topic";
    } else if (data.typeFormatted === "TRANSFORM") {
      data.typeFormatted = "Transform";
    } else if (data.typeFormatted === "DATA_SINK") {
      data.typeFormatted = "Data Sink";
    }

    // save original position for use in animated layouts
    n.data.orgPos = {
      x: n.position.x,
      y: n.position.y,
    };

    // zero width space after dashes to allow for line breaking
    data.name = data.name.replace(/[-]/g, "-\u200B");
  });

  return newObject;
};

onMounted(async () => {
  try {
    loading.value = true;
    await getAllTopics();
    graphData.value = await formatGraphData();
    loading.value = false;
  } catch (error) {
    console.error("Error in Cytoscape mounted():", error);
  }
});
</script>

<style>
#app {
  width: 100%;
  height: 400px;
}
</style>
