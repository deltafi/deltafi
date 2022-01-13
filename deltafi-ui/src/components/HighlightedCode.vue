<template>
  <pre><code :class="classes" v-html="output" /></pre>
</template>

<script>
import "highlight.js/styles/lioshi.css";
import { highlightCode } from '@/workers/highlight.worker'

export default {
  name: "HighlightedCode",
  props: {
    highlight: {
      type: Boolean,
      required: false,
      default: true
    },
    code: {
      type: String,
      required: true
    },
    language: {
      type: String,
      required: false,
      default: null
    }
  },
  data() {
    return {
      result: {
        code: null,
        language: null
      }
    }
  },
  computed: {
    output() {
      return (this.highlight && this.result.code) ? this.result.code : this.escapeHtml(this.code);
    },
    classes() {
      return `hljs ${this.result.language}`;
    }
  },
  watch: {
    highlight: async function() {
      if (this.result.code == null) {
        await this.highlightCode();
      }
    },
  },
  async created() {
    if (this.highlight) await this.highlightCode();
  },
  methods: {
    async highlightCode() {
      let result = await highlightCode(this.code, this.language);
      this.result = result;
    },
    escapeHtml(value) {
      return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#x27;')
    }
  }
};
</script>

<style lang="scss">
@import "@/styles/components/highlighted-code.scss";
</style>