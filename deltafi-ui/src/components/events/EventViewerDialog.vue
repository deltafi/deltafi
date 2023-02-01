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
  <Dialog :header="event.summary" :style="{ width: '40vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false" class="event-dialog">
    <div class="event-content" v-html="markdown(event.content || '_Event has no content_')" />
    <template #footer>
      <div class="d-flex justify-content-between">
        <span class="event-badges">
          <EventSeverityBadge v-tooltip.top="'Severity'" :severity="event.severity" />
          <Tag v-tooltip.top="'Source'" :value="event.source" :rounded="true" icon="pi pi-arrow-circle-right" class="p-tag-secondary" />
          <Tag v-if="event.notification" value="Notification" :rounded="true" icon="pi pi-bell" class="p-tag-secondary" />
          <Timestamp class="text-muted" :timestamp="event.timestamp" />
        </span>
        <span v-if="event.notification && $hasPermission('EventAcknowledge')">
          <Button v-if="event.acknowledged" label="Unacknowledge" class="p-button-sm" @click="unacknowledge()" />
          <Button v-else label="Acknowledge" icon="pi pi-thumbs-up" class="p-button-sm" @click="acknowledge()" />
        </span>
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import EventSeverityBadge from "@/components/events/EventSeverityBadge.vue";
import Timestamp from "@/components/Timestamp.vue";
import useEvents from "@/composables/useEvents";
import { defineProps, toRefs } from "vue";
import MarkdownIt from "markdown-it";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Tag from "primevue/tag";

const { acknowledgeEvent, unacknowledgeEvent } = useEvents();

const markdownIt = new MarkdownIt();
const props = defineProps({
  event: {
    type: Object,
    required: true,
  },
});
const { event } = toRefs(props);

const markdown = (source) => {
  return markdownIt.render(source);
};

const acknowledge = () => {
  acknowledgeEvent(event.value._id);
  event.value.acknowledged = true;
};

const unacknowledge = () => {
  unacknowledgeEvent(event.value._id);
  event.value.acknowledged = false;
};
</script>

<style lang="scss">
.event-dialog {
  .event-badges {
    > * {
      margin-right: 0.5rem;
    }
  }

  .p-button-sm {
    padding: 0 !important;
    padding: 0.25rem 0.4rem !important;
  }
}
</style>