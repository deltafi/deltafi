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
  <div class="deltafile-viewer-page">
    <PageHeader :heading="pageHeader">
      <div class="btn-toolbar">
        <Button v-if="loaded" label="Refresh" :icon="refreshButtonIcon" class="mr-3 p-button p-button-outlined" @click="loadDeltaFileData" />
        <Menu id="config_menu" ref="menu" :model="menuItems" :popup="true" />
        <Button v-if="!showForm" label="Menu" icon="fas fa-ellipsis-v" class="p-button-secondary p-button-outlined" @click="toggleMenu" />
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
      <Message v-if="contentDeleted" severity="warn" :closable="false"> The content for this DeltaFile has been deleted. Reason for this deletion: {{ deltaFile.contentDeletedReason }} </Message>
      <Message v-if="testMode" severity="info" :closable="false">This DeltaFile was processed in test mode. Reason: {{ deltaFile.testModeReason }} </Message>
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileInfoPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-6">
          <DeltaFileParentChildPanel :delta-file-data="deltaFile" field="parentDids" />
        </div>
        <div class="col-6">
          <DeltaFileParentChildPanel :delta-file-data="deltaFile" field="childDids" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-4">
          <DeltaFileDomainsPanel :delta-file-data="deltaFile" />
        </div>
        <div class="col-4">
          <DeltaFileAnnotationsPanel :delta-file-data="deltaFile" />
        </div>
        <div class="col-4">
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
      <HighlightedCode v-if="rawJSONDialog.body" :code="rawJSONDialog.body" />
      <ProgressBar v-else mode="indeterminate" style="height: 0.5em" />
      <ScrollTop target="parent" :threshold="10" icon="pi pi-arrow-up" />
    </Dialog>
    <ConfirmDialog />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="[did]" @acknowledged="onAcknowledged" />
    <MetadataDialog ref="metadataDialog" :did="[did]" @update="loadDeltaFileData" />
    <MetadataDialogResume ref="metadataDialogResume" :did="[did]" @update="loadDeltaFileData" />
    <DialogTemplate component-name="MetadataViewer" header="Metadata" :flow-name="deltaFile.sourceInfo?.flow" :metadata="{ ['All Metadata']: allMetadata }" :deleted-metadata="deletedMetadata" :dismissable-mask="true">
      <span id="cumulativeMetadataDialog" />
    </DialogTemplate>
    <AnnotateDialog ref="annotateDialog" :dids="[did]" @refresh-page="loadDeltaFileData" />
    <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
      <span id="autoResumeDialog" />
    </DialogTemplate>
  </div>
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import DeltaFileActionsPanel from "@/components/DeltaFileActionsPanel.vue";
import DeltaFileDomainsPanel from "@/components/DeltaFileDomainsPanel.vue";
import DeltaFileEnrichmentPanel from "@/components/DeltaFileEnrichmentPanel.vue";
import DeltaFileAnnotationsPanel from "@/components/DeltaFileAnnotationsPanel.vue";
import DeltaFileInfoPanel from "@/components/DeltaFileInfoPanel.vue";
import DeltaFileParentChildPanel from "@/components/DeltaFileParentChildPanel.vue";
import DeltaFileTracePanel from "@/components/DeltaFileTracePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import HighlightedCode from "@/components/HighlightedCode.vue";
import MetadataDialog from "@/components/MetadataDialogReplay.vue";
import MetadataDialogResume from "@/components/errors/MetadataDialogResume.vue";
import useDeltaFiles from "@/composables/useDeltaFiles";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useErrorCount from "@/composables/useErrorCount";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import _ from "lodash";

import Button from "primevue/button";
import ConfirmDialog from "primevue/confirmdialog";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import Message from "primevue/message";
import ScrollTop from "primevue/scrolltop";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const annotateDialog = ref();
const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const uuidRegex = new RegExp(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
const route = useRoute();
const router = useRouter();
const uiConfig = inject("uiConfig");
const { data: deltaFile, getDeltaFile, getRawDeltaFile, cancelDeltaFile, loaded, loading } = useDeltaFiles();
const { fetchErrorCount } = useErrorCount();
const notify = useNotifications();
const { pendingAnnotations } = useDeltaFilesQueryBuilder();
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
const metadataDialog = ref();
const metadataDialogResume = ref();
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
      document.getElementById("cumulativeMetadataDialog").click();
    },
  },
  {
    label: "Replay",
    icon: "fas fa-sync fa-fw",
    visible: () => !beenReplayed.value && !beenDeleted.value && hasPermission("DeltaFileReplay"),
    command: () => {
      metadataDialog.value.showConfirmDialog();
    },
  },
  {
    label: "Cancel",
    icon: "fas fa-power-off fa-fw",
    visible: () => canBeCancelled.value && hasPermission("DeltaFileCancel"),
    command: () => {
      onCancelClick();
    },
  },
  {
    separator: true,
    visible: computed(() => isError.value && !beenDeleted.value && hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume", "ResumePolicyCreate")),
  },
  {
    label: "Acknowledge Error",
    icon: "fas fa-check-circle fa-fw",
    visible: computed(() => isError.value && hasPermission("DeltaFileAcknowledge")),
    command: () => {
      ackErrorsDialog.visible = true;
    },
  },
  {
    label: "Resume",
    icon: "fas fa-redo fa-fw",
    visible: computed(() => isError.value && hasPermission("DeltaFileResume")),
    command: () => {
      metadataDialogResume.value.showConfirmDialog();
    },
  },
  {
    label: "Annotate",
    icon: "fa-solid fa-tags fa-fw",
    visible: computed(() => hasPermission("DeltaFileAnnotate")),
    command: () => {
      annotateDialog.value.showDialog();
    },
  },
  {
    label: "Create Auto Resume Rule",
    icon: "fas fa-clock-rotate-left fa-flip-horizontal fa-fw",
    command: () => {
      document.getElementById("autoResumeDialog").click();
    },
    visible: computed(() => isError.value && hasPermission("ResumePolicyCreate")),
  },
]);

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const contentDeleted = computed(() => {
  return loaded.value && deltaFile.contentDeleted !== null;
});

const testMode = computed(() => {
  return loaded.value && deltaFile.testMode;
});
const deltaFileLinks = computed(() => {
  if (Object.keys(deltaFile).length === 0) return [];

  return uiConfig.deltaFileLinks.map((link) => {
    const output = { ...link };
    const variables = output.url.match(/\$\{[a-zA-Z0-9._-]+}/g);
    const undefinedFields = [];

    for (const v of variables) {
      const deltaFilePath = v.match(/\$\{(.*)}/)[1];
      let value;
      try {
        value = deltaFilePath.split(".").reduce((o, i) => o[i], deltaFile);
      } catch {
        value = undefined;
      }
      if (value === undefined) undefinedFields.push(deltaFilePath);
      output.url = output.url.replace(v, value);
    }

    if (undefinedFields.length > 0) {
      output.issue = `The following required fields are undefined on this DeltaFile: ${undefinedFields.join(", ")}`;
    }
    return output;
  });
});

watch(
  () => deltaFile.annotations,
  () => {
    fetchPendingAnnotations();
  }
);

const fetchPendingAnnotations = async () => {
  let response = await pendingAnnotations(did.value.toLowerCase());
  deltaFile["pendingAnnotations"] = response.data.pendingAnnotations.join(", ");
};

const menuItems = computed(() => {
  let items = staticMenuItems;
  const customLinks = deltaFileLinks.value.map((link) => {
    return {
      label: link.name,
      icon: "fas fa-external-link-alt fa-fw",
      command: () => {
        if (link.issue) {
          notify.error("Unable To Resolve Link", link.issue);
        } else {
          window.open(link.url, "_blank");
        }
      },
    };
  });
  if (customLinks.length > 0) {
    customLinks.unshift({
      separator: true,
    });
  }
  return items.concat(customLinks);
});

const allMetadata = computed(() => {
  if (!loaded.value) return {};
  return Object.entries(deltaFile.metadata).map(([key, value]) => ({ key, value }));
});

const deletedMetadata = computed(() => {
  if (!loaded.value) return {};
  let deletedMetadataList = _.filter(deltaFile.actions, function (o) {
    return o.deleteMetadataKeys.length > 0;
  });

  if (_.isEmpty(deletedMetadataList)) return null;
  return deletedMetadataList;
});

const validDID = computed(() => {
  return uuidRegex.test(did.value);
});

const isError = computed(() => {
  return deltaFile.stage === "ERROR";
});

const beenReplayed = computed(() => {
  return deltaFile.replayed !== null;
});

const beenDeleted = computed(() => {
  return deltaFile.contentDeleted !== null;
});

const hasMetadata = computed(() => {
  return Object.keys(allMetadata.value).length > 0;
});

const canBeCancelled = computed(() => {
  return deltaFile.nextAutoResume !== null || !["COMPLETE", "ERROR", "CANCELLED"].includes(deltaFile.stage);
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
  if (did.value !== did.value.toLowerCase()) {
    router.push({ path: `/deltafile/viewer/${did.value.toLowerCase()}` });
    return;
  }

  try {
    showForm.value = false;
    await getDeltaFile(did.value);
    fetchPendingAnnotations();
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

const viewRawJSON = async () => {
  rawJSONDialog.visible = true;
  rawJSONDialog.header = did.value;
  const raw = await getRawDeltaFile(did.value);
  rawJSONDialog.body = JSON.stringify(JSON.parse(raw), null, 2);
};

const onAcknowledged = (_, reason) => {
  ackErrorsDialog.visible = false;
  notify.success("Successfully Acknowledged Error", reason);
  fetchErrorCount();
  loadDeltaFileData();
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

const onCancelClick = () => {
  confirm.require({
    message: "Are you sure you want to cancel this DeltaFile?",
    header: "Confirm Cancel",
    icon: "pi pi-exclamation-triangle",
    acceptLabel: "Yes",
    rejectLabel: "Cancel",
    accept: () => {
      onCancel();
    },
    reject: () => {},
  });
};

const onCancel = async () => {
  const cancelResponse = await cancelDeltaFile([did.value]);
  if (cancelResponse[0].success) {
    notify.success("Successfully Canceled DeltaFile");
    loadDeltaFileData();
  } else {
    notify.error("Failed to Cancel DeltaFile", cancelResponse[0].error);
  }
};

const autoResumeSelected = computed(() => {
  let newResumeRule = {};
  if (!_.isEmpty(deltaFile) && isError.value) {
    let rowInfo = JSON.parse(JSON.stringify(deltaFile));
    let errorInfo = _.find(deltaFile["actions"], ["state", "ERROR"]);
    newResumeRule["flow"] = rowInfo.sourceInfo.flow;
    newResumeRule["action"] = errorInfo.name;
    newResumeRule["errorSubstring"] = errorInfo.errorCause;
    return newResumeRule;
  } else {
    return {};
  }
});
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-viewer-page.scss";
</style>
