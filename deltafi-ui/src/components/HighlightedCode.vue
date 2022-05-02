<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

<template>
  <pre><code :class="classes" v-html="output" /></pre>
</template>

<script setup>
import "highlight.js/styles/lioshi.css";
import { highlightCode } from "@/workers/highlight.worker";
import { computed, ref, toRefs, watch, defineProps } from "vue";

const props = defineProps({
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
});
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
</script>

<style lang="scss">
@import "@/styles/components/highlighted-code.scss";
</style>