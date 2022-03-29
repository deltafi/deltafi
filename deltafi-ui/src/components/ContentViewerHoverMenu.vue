<template>
  <span :class="containerClass">
    <template v-for="(item, i) of model" :key="i.toString()">
      <!-- Disabled Button -->
      <span v-if="(!item.isEnabled)" v-tooltip.top="disabledLabel(item)" class="button-padding">
        <!-- Disabled Stacked Icons Button -->
        <span v-if="!_.isEmpty(item.alternateIcon)">
          <span class="button-padding">
            <Button type="button" class="p-button p-button-outlined p-button-secondary stacked-icons" :disabled="!item.isEnabled">
              <span class="fa-stack">
                <i :class="item.icon"></i>
                <i :class="item.alternateIcon + ' fa-stack-2x'" style="color:#ed969e"></i>
              </span>
            </Button>
          </span>
        </span>

        <!-- Disabled Single Icon Button -->
        <span v-else class="button-padding">
          <Button type="button" :icon="item.icon" class="p-button p-button-outlined p-button-secondary" :disabled="!item.isEnabled" />
        </span>
      </span>

      <!-- Stacked Icons Button -->
      <span v-else-if="!_.isEmpty(item.alternateIcon)">
        <span v-if="(_.isEmpty(item.toggled) && item.toggled)" class="button-padding">
          <Button v-tooltip.top="item.label" type="button" class="p-button p-button-outlined p-button-secondary stacked-icons" @click="itemClick(item)">
            <span class="fa-stack">
              <i :class="item.icon"></i>
              <i :class="item.alternateIcon + ' fa-stack-2x'" style="color:#ed969e"></i>
            </span>
          </Button>
        </span>
        <span v-else class="button-padding">
          <Button v-tooltip.top="item.alternateLabel" type="button" :icon="item.icon" class="p-button p-button-outlined p-button-secondary" @click="itemClick(item)" />
        </span>
      </span>

      <!-- Single Icon Button -->
      <span v-else class="button-padding">
        <Button v-tooltip.top="item.label" type="button" :icon="item.icon" class="p-button p-button-outlined p-button-secondary" @click="itemClick(item)" />
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
    default: 'window'
  },
  model: {
    type: Array,
    default: null
  }
});

const { target, model } = toRefs(props);

const containerClass = computed(() => {
  return ['context-viewer-hover-menu p-link p-component', { 'context-viewer-hover-menu-sticky': target.value !== 'window' }];
});

const disabledLabel = (item) => {
  return !_.isEmpty(item.disabledLabel) ? item.disabledLabel : 'Disabled ' + item.label;
};

const itemClick = (event) => {
  const item = event;
  if ('toggled' in item) {
    item.toggled = !item.toggled;
  }
  if (item.command) {
    item.command(event);
  }
};
</script>

<style lang="scss">
@import "@/styles/components/content-viewer-hover-menu.scss";
</style>