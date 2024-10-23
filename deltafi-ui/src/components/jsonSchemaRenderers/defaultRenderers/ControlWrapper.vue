<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div v-if="visible" :id="id" :class="styles.control.root">
    <slot></slot>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from "vue";
import { Styles } from "@/components/jsonSchemaRenderers/defaultRenderers/styles/useStyles";

export default defineComponent({
  name: "ControlWrapper",
  props: {
    id: {
      required: true,
      type: String,
    },
    visible: {
      required: false as const,
      type: Boolean,
      default: true,
    },
    styles: {
      required: true,
      type: Object as PropType<Styles>,
    },
  },
});
</script>
