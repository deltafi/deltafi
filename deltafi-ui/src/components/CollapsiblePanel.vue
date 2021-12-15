<template>
  <Panel ref="panelRef" :toggleable="true" v-bind="$attrs" @click="panelHeaderToggle">
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
    <slot />
  </Panel>
</template>

<script>
import Panel from "primevue/panel";

export default {
  name: "CollapsiblePanel",
  components: {
    Panel,
  },
  methods: {
    panelHeaderToggle(event) {
      let panelHeader = event.target.getAttribute("class");
      if (panelHeader == "p-panel-header") {
        this.$refs.panelRef.toggle(event);
      }
    },
  },
};
</script>