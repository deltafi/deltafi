<template>
  <div>
    <div v-if="!_.isEmpty(subscribeData)" @mouseover="showOverlayPanel($event)" @mouseleave="hideOverlayPanel($event)">
      <div v-for="(topic, index) in _.uniq(_.map(subscribeData, 'topic'))" :key="index">
        <div v-if="!_.isEmpty(topic)">
          <span v-if="_.uniq(_.map(subscribeData, 'topic')) > 1">&bull;</span>
          {{ topic }} {{ counts[topic] > 1 ? `(x${counts[topic]})` : "" }}
        </div>
      </div>
    </div>
    <OverlayPanel ref="subscribeOverlayPanel">
      <div class="mb-2 text-muted">
        <strong>Subscriptions</strong>
      </div>
      <div v-for="(subscribe, index) in subscribeData" :key="index" class="ml-2">
        <div v-if="!_.isEmpty(subscribe.topic)">Topic Name: {{ subscribe.topic }}</div>
        <div v-if="!_.isEmpty(subscribe.condition)">Condition: {{ subscribe.condition }}</div>
        <Divider v-if="subscribeData.length > index + 1" class="mt-1 mb-3" />
      </div>
    </OverlayPanel>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import Divider from "primevue/divider";
import OverlayPanel from "primevue/overlaypanel";
import _ from "lodash";

const props = defineProps({
  subscribeData: {
    type: Object,
    required: true,
  },
});

const { subscribeData } = reactive(props);

const subscribeOverlayPanel = ref();
const showOverlayPanel = (event) => {
  subscribeOverlayPanel.value.show(event);
};
const hideOverlayPanel = (event) => {
  subscribeOverlayPanel.value.hide(event);
};

const counts = computed(() => {
  const counts = {};
  _.map(subscribeData, "topic").forEach(function (x) {
    counts[x] = (counts[x] || 0) + 1;
  });
  return counts;
});
</script>
<style />
