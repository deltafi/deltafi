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
  <span>
    <Tag v-tooltip.bottom="tooltip" :class="tagClass" icon="pi pi-bell" :value="tagValue" @click="openNotificationsPanel" />
    <OverlayPanel ref="notificationOP" style="width: 38.25rem; min-width: 31.25rem; top: 2.7rem; max-height: 90vh; overflow: scroll" :base-z-index="2000">
      <div class="list-group list-group-flush">
        <div style="text-align: right; margin-bottom: 0.5rem;">
          <router-link v-slot="{ navigate }" to="/events" custom>
            <Button label="View All Events" class="p-button-sm p-button-text mr-2" @click="navigate(); closeNotificationsPanel();"></Button>
          </router-link>
          <Button v-if="$hasPermission('EventAcknowledge')" label="Acknowledge All" :disabled="notificationCount == 0" icon="fas fa-solid fa-thumbs-up" class="p-button-sm" @click="ackAllNotifications();"></Button>
        </div>
        <div v-if="notifications.length > 0">
          <div v-for="msg in notifications" :key="msg._id" :class="severityClass(msg.severity)" @click="showEvent(msg, $event)">
            <div class="d-flex w-100 justify-content-between">
              <strong class="mb-0">{{ msg.summary }}</strong>
              <div>
                <i v-if="$hasPermission('EventAcknowledge')" v-tooltip.top="'Click to Acknowledge'" class="fas fa-solid fa-thumbs-up cursor-pointer notification-ack-button" @click="ackNotification(msg._id)" />
              </div>
            </div>
            <small class="mb-1 text-muted d-flex w-100 justify-content-between">
              <span>{{ msg.source }}</span>
              <span>{{ getTimeFrom(msg.timestamp) }}</span>
            </small>
          </div>
        </div>
        <div v-else>
          <a style="font-style: italic">No notifications to display</a>
        </div>
      </div>
    </OverlayPanel>
    <EventViewerDialog v-model:visible="showEventDialog" :event="activeEvent"></EventViewerDialog>
  </span>
</template>

<script setup>
import OverlayPanel from "primevue/overlaypanel";
import { useTimeAgo } from "@vueuse/core";
import Tag from "primevue/tag";
import { onMounted, ref, computed, inject, onUnmounted } from "vue";
import Button from "primevue/button";
import useEventsNotifications from "@/composables/useEventsNotifications";
import EventViewerDialog from "@/components/events/EventViewerDialog.vue";
import _ from "lodash";
import useUtilFunctions from "@/composables/useUtilFunctions";

const { pluralize } = useUtilFunctions();
const { notifications, fetchNotifications, ackNotification, ackAllNotifications } = useEventsNotifications();
const refreshInterval = 5000; // 5 seconds
const notificationOP = ref(null);
const showEventDialog = ref(false);
const activeEvent = ref({});
const isIdle = inject("isIdle");

let autoRefresh = null;

onMounted(async () => {
  await fetchNotifications();
  autoRefresh = setInterval(() => {
    if(!isIdle.value) {
    fetchNotifications();
    }
   }, refreshInterval);
});

onUnmounted(()=> {
  clearInterval(autoRefresh)
});

const notificationCount = computed(() => {
  return notifications.value.length;
});

const tagValue = computed(() => {
  return (notificationCount.value > 0) ? notificationCount.value : "";
});

const openNotificationsPanel = (event) => {
  fetchNotifications();
  notificationOP.value.toggle(event);
};

const showEvent = (msg, $event) => {
  // Don't show the event if the user clicked the ack button.
  if ($event.target.className.includes('notification-ack-button')) return;

  activeEvent.value = msg;
  showEventDialog.value = true;
};

const closeNotificationsPanel = () => {
  notificationOP.value.hide();
};

const tagClass = computed(() => {
  return [
    "notification-badge",
    {
      "no-notifications": notificationCount.value === 0,
    },
  ]
})

const getTimeFrom = (msgTimeStamp) => {
  const timeAgo = useTimeAgo(new Date(msgTimeStamp));
  return timeAgo.value;
};

const severityClass = (messageSeverity) => {
  return [
    "list-group-item list-group-item-action cursor-pointer",
    {
      "notification-severity-info": messageSeverity === "info",
      "notification-severity-warning": messageSeverity === "warn",
      "notification-severity-danger": messageSeverity === "error",
      "notification-severity-success": messageSeverity === "success",
    },
  ];
};

const tooltip = computed(() => {
  const notificationsBySeverity = _.groupBy(notifications.value, "severity");
  const severities = ["info", "success", "warn", "error"];
  return severities.reduce(function (result, severity) {
    if (severity in notificationsBySeverity)
      result += "\n" + pluralize(notificationsBySeverity[severity].length, `${severity} notification`);
    return result;
  }, "Click for Notifications\n");
})
</script>

<style lang="scss">
.notification-badge {
  cursor: pointer;
}

.notification-badge.no-notifications {
  background-color: var(--gray-700) !important;

  .p-tag-icon {
    margin-right: 0 !important;
  }
}

.notification-severity-info {
  border-left: 6px solid #17a2b8 !important;
}

.notification-severity-warning {
  border-left: 6px solid #ffc107 !important;
}

.notification-severity-danger {
  border-left: 6px solid #dc3545 !important;
}

.notification-severity-success {
  border-left: 6px solid #28a745 !important;
}

.notification-ack-button {
  margin-left: 0.5rem;
}
</style>
