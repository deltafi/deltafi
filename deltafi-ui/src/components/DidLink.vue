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
  <span ref="link" style="cursor: pointer;">
    <router-link class="monospace" :to="{ path: path }">{{ shortDid }}</router-link>
    <span v-tooltip.right="'Copy full DID to clipboard'" :style="{ visibility: isHovered ? 'visible' : 'hidden' }" class="copy-link" @click.stop="onCopy($event)">
      <i :class="copyLinkIconClass"></i>
    </span>
  </span>
</template>

<script setup>
import { defineProps, computed, ref } from "vue";
import { useElementHover } from '@vueuse/core'
import { useClipboard } from '@vueuse/core'
import useNotifications from "@/composables/useNotifications";

const notify = useNotifications();
const props = defineProps({
  did: {
    type: String,
    required: true,
  }
});
const link = ref();
const isHovered = useElementHover(link)
const { copy, copied } = useClipboard()

const onCopy = (event) => {
  if (!copied.value) {
    copy(props.did);
    notify.info("DID Copied to Clipboard", props.did, 1500);
  }
  console.log(event)
}

const copyLinkIconClass = computed(() => {
  const common = 'fa-fw ml-1';
  const icon = copied.value ? 'fas fa-check' : 'far fa-copy';
  return `${common} ${icon}`;
})

const shortDid = computed(() => {
  return props.did.split('-')[0];
})

const path = computed(() => {
  return `/deltafile/viewer/${props.did}`;
})
</script>
