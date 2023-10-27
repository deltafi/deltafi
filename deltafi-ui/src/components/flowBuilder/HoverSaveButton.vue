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
  <span :class="containerClass">
    <template v-for="(item, i) of model" :key="i.toString()">
      <!-- Disabled Button -->
      <span v-if="!item.isEnabled" v-tooltip.top="disabledLabel(item)" class="button-padding">
        <!-- Disabled Stacked Icons Button -->
        <span v-if="!_.isEmpty(item.alternateIcon)">
          <span class="button-padding">
            <Button type="button" class="p-button p-button-outlined p-button-secondary stacked-icons" :disabled="!item.isEnabled">
              <span class="fa-stack">
                <i :class="item.icon"></i>
                <i :class="item.alternateIcon + ' fa-stack-2x'" style="color: #ed969e"></i>
              </span>
            </Button>
          </span>
        </span>

        <!-- Disabled Single Icon Button -->
        <span v-else class="button-padding">
          <Button type="button" :icon="item.icon" :label="item.label" class="p-button p-button-outlined p-button-secondary" :disabled="!item.isEnabled" />
        </span>
      </span>

      <!-- Stacked Icons Button -->
      <span v-else-if="!_.isEmpty(item.alternateIcon)">
        <span v-if="_.isEmpty(item.toggled) && item.toggled" class="button-padding">
          <Button v-tooltip.top="item.label" type="button" class="p-button p-button-outlined p-button-secondary stacked-icons" @click="itemClick(item)">
            <span class="fa-stack">
              <i :class="item.icon"></i>
              <i :class="item.alternateIcon + ' fa-stack-2x'" style="color: #ed969e"></i>
            </span>
          </Button>
        </span>
        <span v-else class="button-padding">
          <Button v-tooltip.top="item.alternateLabel" type="button" :icon="item.icon" class="p-button p-button-outlined p-button-secondary" @click="itemClick(item)" />
        </span>
      </span>

      <!-- Single Icon Button -->
      <span v-else class="button-padding">
        <Button v-tooltip.top="item.label" :label="item.label" type="button" :icon="item.icon" class="p-button p-button-primary" @click="itemClick(item)" />
      </span>
    </template>
  </span>
</template>

<script setup>
import Button from "primevue/button";
import { computed, defineProps, toRefs } from "vue";
import _ from "lodash";

const props = defineProps({
  target: {
    type: String,
    default: "window",
  },
  model: {
    type: Array,
    default: null,
  },
});

const { target, model } = toRefs(props);

const containerClass = computed(() => {
  return ["context-viewer-hover-menu p-link p-component", { "context-viewer-hover-menu-sticky": target.value !== "window" }];
});

const disabledLabel = (item) => {
  return !_.isEmpty(item.disabledLabel) ? item.disabledLabel : "Disabled " + item.label;
};

const itemClick = (event) => {
  const item = event;
  if ("toggled" in item) {
    item.toggled = !item.toggled;
  }
  if (item.command) {
    item.command(event);
  }
};
</script>

<style lang="scss">
.context-viewer-hover-menu {
  position: fixed;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  justify-content: right;
}

.context-viewer-hover-menu-sticky {
  position: sticky;
}

.context-viewer-hover-menu-sticky.p-link {
  margin-left: auto;
}

.button-padding {
  padding-left: 1px !important;
  padding-right: 1px !important;
}

.stacked-icons {
  width: 2.357rem;
  padding: 0.2rem 0;
}

.transparent-icon {
  background-color: transparent;
}
</style>