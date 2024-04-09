<template>
  <span>
    <template v-if="!_.isEmpty(data[field].defaultRule)">
      <small><u>Default Rule</u></small>
      <div class="ml-2">
        <div>
          <small>Default Behavior: {{ data[field].defaultRule.defaultBehavior }}</small>
        </div>
        <div v-if="!_.isEmpty(data[field].defaultRule.topic)" class="">
          <small v-if="!_.isEmpty(data[field].defaultRule.topic)">Topic: {{ data[field].defaultRule.topic }}</small>
        </div>
      </div>
    </template>
    <template v-if="!_.isEmpty(data[field].matchingPolicy)">
      <div>
        <small><u>Matching Policy</u></small>
      </div>
      <div class="ml-2">
        <small> {{ data[field].matchingPolicy }}</small>
      </div>
    </template>
    <template v-if="!_.isEmpty(data[field].rules)">
      <div>
        <small><u>Rules</u></small>
      </div>
      <div v-for="(rule, index) in data[field].rules" :key="index" class="ml-2">
        <div v-if="!_.isEmpty(rule.topics)">
          <small>Topics: {{ rule.topics.join(", ") }}</small>
        </div>
        <div v-if="!_.isEmpty(rule.condition)">
          <small>Condition: {{ rule.condition }}</small>
        </div>
        <Divider v-if="data[field].rules.length > index + 1" class="mt-1 mb-3" />
      </div>
    </template>
  </span>
</template>

<script setup>
import { defineProps, reactive } from "vue";
import Divider from "primevue/divider";

import _ from "lodash";

const props = defineProps({
  dataProp: {
    type: Object,
    required: true,
  },
  fieldProp: {
    type: String,
    required: true,
  },
});

const { dataProp: data, fieldProp: field } = reactive(props);
</script>
<style lang="scss"></style>
