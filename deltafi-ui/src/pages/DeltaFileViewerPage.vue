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
  <div class="deltafile-viewer">
    <PageHeader :heading="pageHeader">
      <div class="btn-toolbar">
        <Menu id="config_menu" ref="menu" :model="menuItems" :popup="true" />
        <Button v-if="!showForm" label="Menu" icon="fas fa-ellipsis-v" class="p-button p-button-outlined" @click="toggleMenu" />
      </div>
    </PageHeader>
    <ProgressBar v-if="showProgressBar" mode="indeterminate" style="height: 0.5em" />
    <div v-else-if="showForm" class="p-float-label">
      <div class="row">
        <div class="col-4">
          <div class="p-inputgroup">
            <InputText v-model="did" placeholder="DID (UUID)" :class="{ 'p-invalid': did && !validDID }" @keyup.enter="navigateToDID" />
            <Button class="p-button-primary" :disabled="!did || !validDID" @click="navigateToDID">View</Button>
          </div>
          <small v-if="did && !validDID" class="p-error ml-1">Invalid UUID</small>
        </div>
      </div>
    </div>
    <div v-else-if="loaded">
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileInfoPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileParentChildPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-6">
          <DeltaFileDomainsPanel :delta-file-data="deltaFile" />
        </div>
        <div class="col-6">
          <DeltaFileEnrichmentPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileActionsPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileTracePanel :delta-file-data="deltaFile" />
        </div>
      </div>
    </div>
    <Dialog v-model:visible="rawJSONDialog.visible" :header="rawJSONDialog.header" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false">
      <HighlightedCode :code="rawJSONDialog.body" />
      <ScrollTop target="parent" :threshold="10" icon="pi pi-arrow-up" />
    </Dialog>
    <ConfirmDialog />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="[did]" @acknowledged="onAcknowledged" />
    <MetadataViewer ref="metadataViewer" :metadata-references="allMetadata" />
  </div>
</template>

<script setup>
import InputText from "primevue/inputtext";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import ConfirmDialog from "primevue/confirmdialog";
import Menu from "primevue/menu";
import ProgressBar from "primevue/progressbar";
import MetadataViewer from "@/components/MetadataViewer.vue";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import DeltaFileActionsPanel from "@/components/DeltaFileActionsPanel.vue";
import DeltaFileParentChildPanel from "@/components/DeltaFileParentChildPanel.vue";
import DeltaFileDomainsPanel from "@/components/DeltaFileDomainsPanel.vue";
import DeltaFileEnrichmentPanel from "@/components/DeltaFileEnrichmentPanel.vue";
import DeltaFileInfoPanel from "@/components/DeltaFileInfoPanel.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import PageHeader from "@/components/PageHeader.vue";
import useDeltaFiles from "@/composables/useDeltaFiles";
import useErrorCount from "@/composables/useErrorCount";
import useErrorRetry from "@/composables/useErrorRetry";
import useNotifications from "@/composables/useNotifications";
import DeltaFileTracePanel from "@/components/DeltaFileTracePanel.vue";
import { reactive, ref, computed, watch, onMounted, inject } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useConfirm } from "primevue/useconfirm";
import ScrollTop from "primevue/scrolltop";

const uuidRegex = new RegExp(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
const confirm = useConfirm();
const route = useRoute();
const router = useRouter();
const uiConfig = inject("uiConfig");
const { data: deltaFile, getDeltaFile, loaded, loading } = useDeltaFiles();
const { fetchErrorCount } = useErrorCount();
const { retry } = useErrorRetry();
const notify = useNotifications();
const showForm = ref(true);
const did = ref(null);
const ackErrorsDialog = reactive({
  visible: false,
});
const rawJSONDialog = reactive({
  visible: false,
  header: null,
  body: null,
});
const metadataViewer = ref();
const menu = ref();
const staticMenuItems = reactive([
  {
    label: "View Raw DeltaFile",
    icon: "fas fa-file-code fa-fw",
    command: () => {
      viewRawJSON();
    },
  },
  {
    label: "View All Metadata",
    icon: "fas fa-database fa-fw",
    visible: () => hasMetadata.value,
    command: () => {
      metadataViewer.value.showDialog();
    },
  },
  {
    separator: true,
    visible: () => isError.value,
  },
  {
    label: "Acknowledge Error",
    icon: "fas fa-check-circle fa-fw",
    visible: () => isError.value,
    command: () => {
      ackErrorsDialog.visible = true;
    },
  },
  {
    label: "Retry Failed Actions",
    icon: "fas fa-redo fa-fw",
    visible: () => isError.value,
    command: () => {
      retryConfirm();
    },
  },
]);

const menuItems = computed(() => {
  let items = staticMenuItems;
  const customLinks = uiConfig.deltaFileLinks.map((link) => {
    return {
      label: link.name,
      icon: "fas fa-external-link-alt fa-fw",
      command: () => {
        const url = link.url.replace("${DID}", did.value);
        window.open(url, "_blank");
      },
    };
  });
  if (customLinks.length > 0) {
    customLinks.unshift({
      separator: true,
    })
  }
  return items.concat(customLinks);
});

const allMetadata = computed(() => {
  if (!loaded.value) return {};
  let layers = deltaFile.protocolStack.concat(deltaFile.formattedData);
  return layers.reduce((content, layer) => {
    let actions = [layer.action, layer.formatAction].flat().filter((n) => n);
    for (const action of actions) {
      let metadata = action === "IngressAction" ? deltaFile.sourceInfo.metadata : layer.metadata || `${deltaFile.did}-${layer.action}`;
      if (metadata.length > 0) {
        content[action] = metadata;
      }
    }
    return content;
  }, {});
});

const validDID = computed(() => {
  return uuidRegex.test(did.value);
});

const isError = computed(() => {
  return deltaFile.stage === "ERROR";
});

const hasMetadata = computed(() => {
  return Object.keys(allMetadata.value).length > 0;
});

const pageHeader = computed(() => {
  let header = ["DeltaFile Viewer"];
  if (did.value && !showForm.value) header.push(did.value);
  return header.join(": ");
});

const showProgressBar = computed(() => {
  return loading.value && !loaded.value;
});

const loadDeltaFileData = async () => {
  try {
    showForm.value = false;
    await getDeltaFile(did.value);
  } catch {
    showForm.value = true;
  }
};

const clearData = () => {
  did.value = null;
  loaded.value = false;
};

const navigateToDID = () => {
  if (did.value === null) {
    return;
  } else if (did.value === route.params.did) {
    loadDeltaFileData();
  } else if (validDID.value) {
    router.push({ path: `/deltafile/viewer/${did.value}` });
  }
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const viewRawJSON = () => {
  rawJSONDialog.visible = true;
  rawJSONDialog.header = did.value;
  rawJSONDialog.body = JSON.stringify(deltaFile, null, 2);
};

const onAcknowledged = (_, reason) => {
  ackErrorsDialog.visible = false;
  notify.success("Successfully Acknowledged Error", reason);
  fetchErrorCount();
  loadDeltaFileData();
};

const retryConfirm = () => {
  confirm.require({
    message: "Are you sure you want to retry this DeltaFile?",
    accept: () => {
      requestRetry();
    },
  });
};

const requestRetry = async () => {
  try {
    const response = await retry([did.value]);
    const result = response.value.data.retry.find((r) => {
      return r.did == did.value;
    });
    if (result.success) {
      notify.success("Retry request sent successfully", did.value);
    } else {
      throw Error(result.error);
    }
  } catch (error) {
    notify.error("Retry request failed", error);
  }
};

watch(
  () => route.params.did,
  (value) => {
    clearData();
    if (value) {
      did.value = value;
      loadDeltaFileData();
    } else {
      showForm.value = true;
    }
  }
);

onMounted(() => {
  if (route.params.did) {
    did.value = route.params.did;
    loadDeltaFileData();
  }
});
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-viewer-page.scss";
</style>
