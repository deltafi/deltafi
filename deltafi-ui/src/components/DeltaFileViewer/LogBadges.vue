<template>
  <div class="log-badges">
    <Badge v-if="props.errorCount" v-tooltip.top="`${pluralize(props.errorCount, 'error log', 'error logs')}`" :value="props.errorCount" severity="danger" @click="emit('click', 'ERROR')" class="mr-1 cursor-pointer"></Badge>
    <Badge v-if="props.warningCount" v-tooltip.top="`${pluralize(props.warningCount, 'warning log', 'warning logs')}`" :value="props.warningCount" severity="warning" @click="emit('click', 'WARNING')" class="mr-1 cursor-pointer"></Badge>
    <Badge v-if="props.userCount" v-tooltip.top="`${pluralize(props.userCount, 'user note', 'user notes')}`" :value="props.userCount" severity="primary" @click="emit('click', 'USER')" class="cursor-pointer"></Badge>
  </div>
</template>

<script setup>
import Badge from "primevue/badge";

import useUtilFunctions from "@/composables/useUtilFunctions";

const { pluralize } = useUtilFunctions();

const props = defineProps({
  errorCount: {
    type: Number,
    required: true,
  },
  warningCount: {
    type: Number,
    required: true,
  },
  userCount: {
    type: Number,
    required: true,
  },
});

const emit = defineEmits(["click"]);
</script>
