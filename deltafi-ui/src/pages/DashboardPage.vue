<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="dashboard-page">
    <PageHeader heading="Dashboard">
      <Button class="p-button p-button-outlined deltafi-input-field" icon="fa fa-sync-alt" label="Refresh" @click="refreshDashboard" />
    </PageHeader>
    <div class="row">
      <div class="col-12">
        <DeltaFileStats :key="refreshKey" />
      </div>
    </div>
    <div class="row">
      <div class="col-12">
        <MetricsPanel :key="refreshKey" />
      </div>
    </div>
    <div class="row">
      <div v-for="(components, column) in columns" :key="column" :class="`col-6 ${column}`">
        <div v-for="component of components" :key="component">
          <Component :is="loadComponent(component)" :key="refreshKey" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import MetricsPanel from "@/components/dashboard/MetricsPanel.vue";
import { defineAsyncComponent, reactive, ref } from "vue";
import DeltaFileStats from "@/components/dashboard/DeltaFileStats.vue";
import Button from "primevue/button";

const refreshKey = ref(0);
const refreshDashboard = () => (refreshKey.value += 1);

const columns = reactive({
  one: ["InstalledPlugins"],
  two: ["ExternalLinks"],
});

const loadComponent = (element) => {
  return defineAsyncComponent(async () => await import(`@/components/dashboard/${element}.vue`));
};
</script>

<style>
.dashboard-page {
  .row {
    .col-6.one {
      padding-right: 8px;
    }

    .col-6.two {
      padding-left: 8px;
    }

    .links-panel,
    .chart-panel {
      .p-panel-content {
        padding: 0;

        strong {
          font-weight: 600;
        }

        .list-group-flush {
          border-radius: 4px;
        }
      }
    }
  }
}
</style>
