<template>
  <span>
    <template v-if="!_.isEmpty(publishData.defaultRule)">
      <div class="mb-2 text-muted">
        <strong>Publish Rules</strong>
      </div>
      <u>Default Rule</u>
      <div class="ml-2">
        <div>
          Default Behavior: {{ publishData.defaultRule.defaultBehavior }}
        </div>
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
        <div v-if="!_.isEmpty(rule.topic)">
          Topic: {{ rule.topic }}
        </div>
        <div v-if="!_.isEmpty(rule.condition)">
          Condition: {{ rule.condition }}
        </div>
        <Divider v-if="publishData.rules.length > index + 1" class="mt-1 mb-3" />
      </div>
    </template>
  </span>
</template>

<script setup>
import { defineProps, reactive } from "vue";
import Divider from "primevue/divider";

import _ from "lodash";

const props = defineProps({
  publishData: {
    type: Object,
    required: true,
  }
});

const { publishData } = reactive(props);
</script>
<style lang="scss"></style>