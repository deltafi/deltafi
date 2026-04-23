<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <pre v-if="!foldable" :class="{ 'line-numbers-pre': lineNumbers }"><code :class="classes" v-html="output" /></pre>
  <pre v-else class="foldable-pre"><code :class="classes"><div
      v-for="(line, i) in foldableLines"
      :key="i"
      v-show="!line.hidden"
      class="fold-line"
    ><span v-if="lineNumbers" class="line-number">{{ i + 1 }}</span><span
        v-if="line.foldStart"
        class="fold-toggle"
        :class="{ collapsed: line.collapsed }"
        @click="toggleFold(i)"
      /><span
        v-else
        class="fold-toggle-spacer"
      /><span v-html="line.collapsed ? line.collapsedHtml : line.html" /></div></code></pre>
</template>

<script setup>
import "highlight.js/styles/lioshi.css";
import { highlightCode } from "@/workers/highlight.worker";
import { computed, ref, reactive, toRefs, watch } from "vue";

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
  lineNumbers: {
    type: Boolean,
    required: false,
    default: false,
  },
  foldable: {
    type: Boolean,
    required: false,
    default: false,
  },
});
const { highlight, code, language, lineNumbers, foldable } = toRefs(props);
const result = ref({
  code: null,
  language: null,
});

watch(highlight, () => {
  if (result.value.code == null) doHighlight();
});

watch(code, () => {
  doHighlight();
  resetFoldState();
});

const addLineNumbers = (html) => {
  const lines = html.split("\n");
  return lines.map((line, i) => `<span class="line-number">${i + 1}</span>${line}`).join("\n");
};

const output = computed(() => {
  const raw = highlight.value && result.value.code ? result.value.code : escapeHtml(code.value);
  return lineNumbers.value ? addLineNumbers(raw) : raw;
});

const classes = computed(() => {
  return `hljs ${result.value.language}`;
});

const doHighlight = async () => {
  result.value = highlight.value ? await highlightCode(code.value, language.value) : code.value;
};

const escapeHtml = (value) => {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#x27;");
};

// --- Fold logic ---

const computeFoldRegions = (rawCode) => {
  const lines = rawCode.split("\n");
  const regions = [];
  const stack = [];

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trimEnd();
    const lastChar = trimmed.slice(-1);
    const lastTwoChars = trimmed.slice(-2);

    // Detect opening brackets at end of line (with optional trailing comma)
    if (lastChar === "{" || lastChar === "[" || lastTwoChars === "{," || lastTwoChars === "[,") {
      stack.push(i);
    }

    // Detect closing brackets
    const firstNonSpace = trimmed.trimStart().charAt(0);
    if (firstNonSpace === "}" || firstNonSpace === "]") {
      if (stack.length > 0) {
        const start = stack.pop();
        if (i > start + 1) {
          regions.push({ start, end: i });
        }
      }
    }
  }

  return regions;
};

const foldState = reactive({});

const resetFoldState = () => {
  Object.keys(foldState).forEach((k) => delete foldState[k]);
};

const foldRegions = computed(() => {
  return computeFoldRegions(code.value);
});

const foldRegionsByStart = computed(() => {
  const map = {};
  for (const r of foldRegions.value) {
    map[r.start] = r;
  }
  return map;
});

const foldableLines = computed(() => {
  const highlightedHtml = highlight.value && result.value.code ? result.value.code : escapeHtml(code.value);
  const htmlLines = highlightedHtml.split("\n");
  const rawLines = code.value.split("\n");

  // Determine which lines are hidden by a collapsed parent
  const hiddenLines = new Set();
  for (const region of foldRegions.value) {
    if (foldState[region.start]) {
      for (let i = region.start + 1; i <= region.end; i++) {
        hiddenLines.add(i);
      }
    }
  }

  return htmlLines.map((html, i) => {
    const region = foldRegionsByStart.value[i];
    const isCollapsed = region && foldState[region.start];

    let collapsedHtml = html;
    if (region && isCollapsed) {
      // Build collapsed preview: opening line + ... + closing bracket
      const rawClosing = rawLines[region.end]?.trimStart() || "";
      const closingChar = rawClosing.charAt(0);
      const trailing = rawClosing.slice(1);
      const count = region.end - region.start - 1;
      collapsedHtml = `${html}<span class="fold-placeholder"> ... ${count} ${count === 1 ? "line" : "lines"} </span>${escapeHtml(closingChar)}${escapeHtml(trailing)}`;
    }

    return {
      html,
      collapsedHtml,
      foldStart: !!region,
      collapsed: isCollapsed,
      hidden: hiddenLines.has(i),
    };
  });
});

const toggleFold = (lineIndex) => {
  foldState[lineIndex] = !foldState[lineIndex];
};

if (highlight.value && result.value.code == null) {
  doHighlight();
}
</script>

<style>
.hljs {
  overflow: auto;
  white-space: pre-wrap;
  white-space: -moz-pre-wrap;
  white-space: -pre-wrap;
  white-space: -o-pre-wrap;
  word-wrap: break-word;
  overflow-wrap: break-word;
  word-break: break-all;
  height: auto;
}

.line-numbers-pre {
  padding-left: 0;
}

.line-number {
  display: inline-block;
  min-width: 2em;
  padding-right: 0.5em;
  text-align: right;
  color: var(--df-muted-color, #999);
  user-select: none;
  border-right: 1px solid var(--df-border-color, #ddd);
  margin-right: 1em;
}

.foldable-pre {
  padding-left: 0;
  margin: 0;
}

.foldable-pre .fold-line {
  white-space: pre;
}

.fold-toggle {
  display: inline-block;
  width: 1.2em;
  cursor: pointer;
  user-select: none;
  text-align: center;
  color: var(--df-muted-color, #999);
  flex-shrink: 0;
}

.fold-toggle::before {
  content: "\25BE";
}

.fold-toggle.collapsed::before {
  content: "\25B8";
}

.fold-toggle:hover {
  color: var(--df-body-color, #fff);
}

.fold-toggle-spacer {
  display: inline-block;
  width: 1.2em;
}

.fold-placeholder {
  color: var(--df-muted-color, #888);
  background-color: rgba(255, 255, 255, 0.07);
  border-radius: 3px;
  padding: 0 0.3em;
  font-style: italic;
  cursor: default;
}
</style>
