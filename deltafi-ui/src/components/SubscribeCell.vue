<template>
  <span>
    <template v-if="!_.isEmpty(subscribeData)">
      <div class="mb-2 text-muted">
        <strong>Subscriptions</strong>
      </div>
      <div v-for="(subscribe, index) in subscribeData" :key="index" class="ml-2">
        <div v-if="!_.isEmpty(subscribe.topic)">
          Topic: {{ subscribe.topic }}
        </div>
        <div v-if="!_.isEmpty(subscribe.condition)">
          Condition: {{ subscribe.condition }}
        </div>
        <Divider v-if="subscribeData.length > index + 1" class="mt-1 mb-3" />
      </div>
    </template>
  </span>
</template>

<script setup>
import { defineProps, reactive } from "vue";
import Divider from "primevue/divider";

import _ from "lodash";

const props = defineProps({
  subscribeData: {
    type: Object,
    required: true,
  }
});

const { subscribeData } = reactive(props);
</script>
<style lang="scss"></style>