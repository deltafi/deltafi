<template>
  <highlightjs v-bind="$attrs" />
</template>

<script>
import "highlight.js/lib/common";
import "highlight.js/styles/lioshi.css";
import hljsVuePlugin from "@highlightjs/vue-plugin";

export default {
  name: "HighlightedCode",
  components: {
    highlightjs: hljsVuePlugin.component,
  },
};
</script>