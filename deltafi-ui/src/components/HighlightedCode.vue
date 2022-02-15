<template>
  <pre><code :class="classes" v-html="output" /></pre>
</template>

<script>
import "highlight.js/styles/lioshi.css";
import { highlightCode } from "@/workers/highlight.worker";
import { computed, ref, toRefs, watch } from "vue";

export default {
  name: "HighlightedCode",
  props: {
    highlight: {
      type: Boolean,
      required: false,
      default: true,
    },
    code: {
      type: String,
      required: true,
    },
    language: {
      type: String,
      required: false,
      default: null,
    },
  },
  setup(props) {
    const { highlight, code, language } = toRefs(props);
    const result = ref({
      code: null,
      language: null,
    });

    watch(highlight, () => {
      if (result.value.code == null) doHighlight();
    });

    const output = computed(() => {
      return highlight.value && result.value.code ? result.value.code : escapeHtml(code.value);
    });

    const classes = computed(() => {
      return `hljs ${result.value.language}`;
    });

    const doHighlight = async () => {
      result.value = await highlightCode(code.value, language.value);
    };

    const escapeHtml = (value) => {
      return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#x27;");
    };

    if (highlight.value && result.value.code == null) {
      doHighlight();
    }

    return {
      classes,
      output,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/components/highlighted-code.scss";
</style>