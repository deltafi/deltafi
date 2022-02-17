<template>
  <Panel ref="panelRef" :toggleable="true" v-bind="$attrs" @click="panelHeaderToggle">
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
    <slot />
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import { ref } from "vue";

const panelRef = ref(null);

const panelHeaderToggle = (event) => {
  let panelHeader = event.target.getAttribute("class");
  if (panelHeader == "p-panel-header") {
    panelRef.value.toggle(event);
  }
};
</script>