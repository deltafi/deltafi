<template>
  <span>
    <template v-if="!_.isEmpty(data[field])">
      <div>
        <small><u>Subscribe</u></small>
      </div>
      <div v-for="(subscribe, index) in data[field]" :key="index" class="ml-2">
        <div v-if="!_.isEmpty(subscribe.topic)">
          <small>Topic: {{ subscribe.topic }}</small>
        </div>
        <div v-if="!_.isEmpty(subscribe.condition)">
          <small>Condition: {{ subscribe.condition }}</small>
        </div>
        <Divider v-if="data[field].length > index + 1" class="mt-1 mb-3" />
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
