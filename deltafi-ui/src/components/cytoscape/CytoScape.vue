<template>
  <div ref="container">
    <slot />
  </div>
</template>

<script setup>
import Controller from "@/components/cytoscape/useCytoscapeController";
import { onMounted, onUnmounted, provide, ref, toRefs } from "vue";

import Cytoscape from "cytoscape";
import { cxtMenuDefaults, cytoEvent, layout, style } from "./config";
import cxtmenu from "cytoscape-cxtmenu";

Cytoscape.use(cxtmenu);

// Define props in plain JavaScript
const props = defineProps({
  graphData: {
    type: Object,
    required: true,
  },
});

// Refs
const container = ref(null);
const { graphData } = toRefs(props);
const onTap = ref(null);
const controller = ref(null);
const cy = ref(null);

// Provide the Cytoscape instance as a promise
provide(
  "cy",
  { cy, controller }
);
const clearChanges = () => {
  controller.value.clearPaths();
}

onMounted(async () => {
  try {
    // Optional container configuration
    container.value?.setAttribute("id", "cytoscape-div");
    container.value?.setAttribute("width", "100%");
    container.value?.setAttribute("style", "height: 90vh;");

    // Create Cytoscape instance
    const cyInstance = new Cytoscape({
      elements: graphData.value,
      style: style,
      layout: layout,
      container: container.value,
      selectionType: "single",
      boxSelectionEnabled: false,
    });
    cyInstance.nodes().panify().ungrabify();

    // Imports the CXT menu around nodes
    // cyInstance.cxtmenu(cxtMenuDefaults);

    controller.value = new Controller(cyInstance);

    cyInstance.on(
      "tap",
      (onTap.value = (e) => {
        // If its the background, clear highlight, hide the info, and close the menu
        const tappedNode = e.target;
        if (tappedNode === cyInstance) {
          clearChanges();
          // cyInstance.maxZoom(3.5);
          cyInstance.fit();
          // cyInstance.maxZoom(10);
          return;
        }

        const nodeData = tappedNode.data();
        if (nodeData.type.includes("DATA_SOURCE")) {
          clearChanges();
          controller.value.showPathFromDataSource(nodeData);
        } else if (nodeData.type == "DATA_SINK") {
          clearChanges();
          controller.value.showPathToDataSink(nodeData);
        } else {
          clearChanges();
          controller.value.showAllPaths(nodeData);
        }
      })
    );

    cyInstance.fit();

    // Register Cytoscape events
    for (const eventType in [cytoEvent.DRAG, cytoEvent.TAP, cytoEvent.ZOOM, cytoEvent.PAN]) {
      cyInstance.on(eventType, (event) => {
        emit(eventType, event);
      });
    }

    cy.value = cyInstance;
  } catch (error) {
    console.error("Error in Cytoscape mounted():", error);
  }
});

onUnmounted(() => {
  cy.value.removeListener("tap", onTap.value);
});
</script>
