<template>
  <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
    <h1 class="h2">{{ props.heading }}</h1>
    <slot></slot>
  </div>
</template>

<script setup>
import { defineProps } from 'vue';

const props = defineProps({
  heading: {
    type: String,
    required: true,
  },
});
</script>
