<template>
  <div>
    <div v-if="!_.isEmpty(subscribeData)">
      <div v-for="(topic, index) in _.uniq(_.map(subscribeData, 'topic'))" :key="index">
        <div v-if="!_.isEmpty(topic)">
          <span v-if="_.uniq(_.map(subscribeData, 'topic')) > 1">&bull;</span>
          <TopicPublishers :topic-name="topic" />
          {{ topic }} {{ counts[topic] > 1 ? `(x${counts[topic]})` : "" }}
          <i v-if="conditionsByTopic[topic]?.length" class="fa-solid fa-circle-info text-muted ml-1 cursor-pointer" @click="showOverlayPanel($event, topic)"></i>
        </div>
      </div>
    </div>
    <OverlayPanel ref="subscribeOverlayPanel">
      <div class="mb-2 text-muted">
        <strong>Condition</strong>
      </div>
      {{ conditionsByTopic[activeTopic][0] }}
    </OverlayPanel>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref } from "vue";
import OverlayPanel from "primevue/overlaypanel";
import TopicPublishers from "@/components/topics/TopicPublishers.vue";
import _ from "lodash";

const props = defineProps({
  subscribeData: {
    type: Object,
    required: true,
  },
});

const activeTopic = ref(null);
const { subscribeData } = reactive(props);

const subscribeOverlayPanel = ref();
const showOverlayPanel = async (event, topic) => {
  subscribeOverlayPanel.value.hide(event);
  await nextTick()
  activeTopic.value = topic;
  subscribeOverlayPanel.value.show(event);
};

const conditionsByTopic = computed(() => {
  const conditions = {};
  subscribeData.forEach(function (x) {
    if (!conditions[x.topic]) {
      conditions[x.topic] = [];
    }
    if (x.condition) {
      conditions[x.topic].push(x.condition);
    }
  });
  return conditions;
});

const counts = computed(() => {
  const counts = {};
  _.map(subscribeData, "topic").forEach(function (x) {
    counts[x] = (counts[x] || 0) + 1;
  });
  return counts;
});
</script>
<style />
