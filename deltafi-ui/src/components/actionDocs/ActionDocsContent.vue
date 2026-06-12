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

<!--
  Renders an action's generated documentation Markdown (ActionDescriptor.docsMarkdown) with the
  parameter table, Types section, etc. Self-contained so it can be hosted anywhere: the plugin
  ActionDocsDrawer and the flow builder's Action Editor reference pane both mount this.
-->
<template>
  <!-- eslint-disable-next-line vue/no-v-html -- rendered HTML is sanitized with DOMPurify (see markdownDocs.js) -->
  <div v-if="docsHtml" ref="docsEl" class="action-docs-markdown" v-html="docsHtml" @click="onTypeLinkClick" />
  <p v-else class="action-docs-empty">No documentation available for this action.</p>
</template>

<script setup>
import { computed, ref } from "vue";
import MarkdownIt from "markdown-it";
import { sanitizeDocsHtml } from "@/utils/markdownDocs";

// Core generates the documentation as Markdown that intentionally contains HTML (<font>, <br/>, etc.),
// so we render with html:true and sanitize the result with DOMPurify (see markdownDocs.js) to keep
// the rich formatting while stripping any XSS that a plugin's docs could otherwise inject.
const markdownIt = new MarkdownIt({ html: true, linkify: true, breaks: true });

// Give every heading the same id the generated [`Type`](#type) links target, so clicking a type link
// can scroll to it. Matches DocumentationGenerator.slug(): lowercase, runs of non-alphanumerics -> "-", trimmed.
function slugify(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}
markdownIt.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const inline = tokens[idx + 1];
  if (inline && inline.type === "inline" && inline.content) {
    tokens[idx].attrSet("id", slugify(inline.content));
  }
  return self.renderToken(tokens, idx, options);
};

const props = defineProps({
  markdown: {
    type: String,
    default: "",
  },
});

const docsEl = ref(null);
const docsHtml = computed(() => (props.markdown ? sanitizeDocsHtml(markdownIt.render(props.markdown)) : ""));

// Intercept the in-doc type links so they scroll to the type's heading within this component's scroll
// container instead of navigating the page URL (a #hash would not move the host's own scroll area).
function onTypeLinkClick(event) {
  const link = event.target.closest('a[href^="#"]');
  if (!link) {
    return;
  }
  event.preventDefault();
  const id = decodeURIComponent(link.getAttribute("href").slice(1));
  const target = id && docsEl.value ? docsEl.value.querySelector(`[id="${CSS.escape(id)}"]`) : null;
  if (!target) {
    return;
  }
  target.scrollIntoView({ behavior: "smooth", block: "start" });
  target.classList.add("ad-flash");
  setTimeout(() => target.classList.remove("ad-flash"), 1200);
}
</script>

<style scoped>
.action-docs-empty {
  color: var(--p-text-muted-color, #6c757d);
}
.action-docs-markdown :deep(table) {
  border-collapse: collapse;
  margin: 0.5rem 0 1rem;
  font-size: 0.86rem;
}
.action-docs-markdown :deep(th),
.action-docs-markdown :deep(td) {
  border: 1px solid var(--p-content-border-color, #e2e2e3);
  padding: 0.3rem 0.5rem;
  text-align: left;
  vertical-align: top;
}
.action-docs-markdown :deep(th) {
  background: var(--p-content-hover-background, #f6f6f7);
}
.action-docs-markdown :deep(code) {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 0.85em;
}
.action-docs-markdown :deep(pre) {
  background: var(--p-content-hover-background, #f6f6f7);
  padding: 0.75rem;
  border-radius: 6px;
  overflow-x: auto;
}
.action-docs-markdown :deep(h1) {
  font-size: 1.4rem;
  margin: 0 0 0.25rem;
}
.action-docs-markdown :deep(h2) {
  font-size: 1.1rem;
  margin: 1.25rem 0 0.5rem;
  padding-bottom: 0.2rem;
  border-bottom: 1px solid var(--p-content-border-color, #e2e2e3);
}
.action-docs-markdown :deep(h3) {
  font-size: 0.98rem;
  margin: 1rem 0 0.4rem;
}
.action-docs-markdown :deep(h1),
.action-docs-markdown :deep(h2),
.action-docs-markdown :deep(h3) {
  scroll-margin-top: 0.5rem;
  border-radius: 4px;
  transition: background-color 0.6s ease;
}
/* brief highlight when a type link scrolls to its heading */
.action-docs-markdown :deep(.ad-flash) {
  background-color: color-mix(in srgb, var(--p-primary-color, #3b82f6) 20%, transparent);
}
.action-docs-markdown :deep(a) {
  cursor: pointer;
  color: var(--p-primary-color, #3b82f6);
  text-decoration: none;
}
.action-docs-markdown :deep(a:hover) {
  text-decoration: underline;
}
</style>
