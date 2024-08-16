<template>
  <div>
    <div v-if="!_.isEmpty(publishData.rules)" @mouseover="showOverlayPanel($event)" @mouseleave="hideOverlayPanel($event)">
      <div v-for="(rule, index) in publishData.rules" :key="index">
        <div v-if="!_.isEmpty(rule.topic)">
          <span v-if="publishData.rules.length > 1">&bull;</span>
          {{ rule.topic }}
        </div>
      </div>
    </div>
    <OverlayPanel ref="publishOverlayPanel">
      <template v-if="!_.isEmpty(publishData.defaultRule)">
        <div class="mb-2 text-muted">
          <strong>Publish Rules</strong>
        </div>
        <u>Default Rule</u>
        <div class="ml-2">
          <div>Default Behavior: {{ publishData.defaultRule.defaultBehavior }}</div>
          <div v-if="!_.isEmpty(publishData.defaultRule.topic)" class="">
            <span v-if="!_.isEmpty(publishData.defaultRule.topic)">Topic: {{ publishData.defaultRule.topic }}</span>
          </div>
        </div>
      </template>
      <template v-if="!_.isEmpty(publishData.matchingPolicy)">
        <div>
          <u>Matching Policy</u>
        </div>
        <div class="ml-2">
          {{ publishData.matchingPolicy }}
        </div>
      </template>
      <template v-if="!_.isEmpty(publishData.rules)">
        <div>
          <u>Rules</u>
        </div>
        <div v-for="(rule, index) in publishData.rules" :key="index" class="ml-2">
          <div v-if="!_.isEmpty(rule.topic)">Topic: {{ rule.topic }}</div>
          <div v-if="!_.isEmpty(rule.condition)">Condition: {{ rule.condition }}</div>
          <Divider v-if="publishData.rules.length > index + 1" class="mt-1 mb-3" />
        </div>
      </template>
    </OverlayPanel>
  </div>
</template>

<script setup>
import { defineProps, reactive, ref } from "vue";
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
</script>
<style lang="scss"></style>