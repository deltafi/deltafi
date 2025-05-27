<template>
  <div>
    <div v-if="!_.isEmpty(publishData.rules)" @mouseover="showOverlayPanel($event)" @mouseleave="hideOverlayPanel($event)">
      <div v-if="!_.isEmpty(publishData.defaultRule.topic)">
        <span v-if="publishData.rules.length > 1">&bull;</span>
        {{ publishData.defaultRule.topic }}
      </div>
      <div v-for="(topic, index) in _.uniq(_.map(publishData.rules, 'topic'))" :key="index">
        <div v-if="!_.isEmpty(topic)">
          <span v-if="publishData.rules.length > 1">&bull;</span>
          {{ topic }} {{ counts[topic] > 1 ? `(x${counts[topic]})` : '' }}
        </div>
      </div>
    </div>
    <OverlayPanel ref="publishOverlayPanel">
      <template v-if="!_.isEmpty(publishData.defaultRule)">
        <div class="mb-2 text-muted">
          <strong>Publish Rules</strong>
        </div>
        <template v-if="!_.isEmpty(publishData.rules)">
          <div>
            <u>Topics</u>
          </div>
          <div v-for="(rule, index) in publishData.rules" :key="index" class="ml-2">
            <div v-if="!_.isEmpty(rule.topic)">
              Topic Name: {{ rule.topic }}
            </div>
            <div v-if="!_.isEmpty(rule.condition)">
              Condition: {{ rule.condition }}
            </div>
            <Divider v-if="publishData.rules.length > index + 1" class="mt-1 mb-3" />
          </div>
        </template>
        <template v-if="!_.isEmpty(publishData.matchingPolicy)">
          <div>
            <u>If multiple topics would receive data:</u>
          </div>
          <div class="ml-2">
            {{ formatDisplayValue(publishData.matchingPolicy) }}
          </div>
        </template>
        <u>If no topics would receive data:</u>
        <div class="ml-2">
          <div>{{ formatDisplayValue(publishData.defaultRule.defaultBehavior) }}</div>
          <div v-if="!_.isEmpty(publishData.defaultRule.topic)" class="">
            <span v-if="!_.isEmpty(publishData.defaultRule.topic)">Topic Name: {{ publishData.defaultRule.topic }}</span>
          </div>
        </div>
      </template>
    </OverlayPanel>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import Divider from "primevue/divider";
import OverlayPanel from "primevue/overlaypanel";
import _ from "lodash";

const props = defineProps({
  publishData: {
    type: Object,
    required: true,
  },
});

const { publishData } = reactive(props);

const publishOverlayPanel = ref();
const showOverlayPanel = (event) => {
  publishOverlayPanel.value.show(event);
};
const hideOverlayPanel = (event) => {
  publishOverlayPanel.value.hide(event);
};

const counts = computed(() => {
  const counts = {};
  _.map(publishData.rules, 'topic').forEach(function (x) { counts[x] = (counts[x] || 0) + 1; });
  return counts;
});

const formatDisplayValue = (valueToDisplay) => {
  if (_.isEqual(valueToDisplay, "ALL_MATCHING")) {
    return "Send to all";
  } else if (_.isEqual(valueToDisplay, "FIRST_MATCHING")) {
    return "Send to first";
  } else {
    return _.startCase(_.lowerCase(valueToDisplay));
  }
};
</script>
<style />
