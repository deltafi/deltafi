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
            <Button class="p-button-primary" :disabled="!did || !validDID" @click="navigateToDID"> View </Button>
          </div>
          <small v-if="did && !validDID" class="p-error ml-1">Invalid UUID</small>
        </div>
      </div>
    </div>
    <div v-else-if="loaded">
      <Message v-if="deltaFile.pinned" severity="warn" :closable="false"> This DeltaFile is pinned and won't be deleted by delete policies </Message>
      <Message v-if="contentDeleted" severity="warn" :closable="false"> The content for this DeltaFile has been deleted. Reason for this deletion: {{ deltaFile.contentDeletedReason }} </Message>
      <Message v-if="testMode" severity="info" :closable="false"> This DeltaFile was processed in test mode. </Message>
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
        <div class="col-6">
          <ContentTagsPanel :delta-file-data="deltaFile" />
        </div>
        <div class="col-6">
          <DeltaFileAnnotationsPanel :delta-file-data="deltaFile" />
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <DeltaFileFlowsPanel :delta-file-data="deltaFile" />
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
    <ReplayDialog ref="replayDialog" :did="[did]" @refresh-page="loadDeltaFileData" />
    <ResumeDialog ref="resumeDialog" :did="[did]" @refresh-page="loadDeltaFileData" />
    <AnnotateDialog ref="annotateDialog" :dids="[did]" @refresh-page="loadDeltaFileData" />
    <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
      <span id="autoResumeDialog" />
    </DialogTemplate>
  </div>
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import DeltaFileFlowsPanel from "@/components/DeltaFileViewer/DeltaFileFlowsPanel.vue";
import DeltaFileAnnotationsPanel from "@/components/DeltaFileAnnotationsPanel.vue";
import DeltaFileInfoPanel from "@/components/DeltaFileInfoPanel.vue";
import DeltaFileParentChildPanel from "@/components/DeltaFileViewer/DeltaFileParentChildPanel.vue";
import ContentTagsPanel from "@/components/DeltaFileViewer/ContentTagsPanel.vue";
import DeltaFileTracePanel from "@/components/DeltaFileTracePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import ReplayDialog from "@/components/ReplayDialog.vue";
import ResumeDialog from "@/components/errors/ResumeDialog.vue";
import useDeltaFiles from "@/composables/useDeltaFiles";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useErrorCount from "@/composables/useErrorCount";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import DialogTemplate from "@/components/DialogTemplate.vue";

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
const { data: deltaFile, getDeltaFile, getRawDeltaFile, cancelDeltaFile, pin, unpin, loaded, loading } = useDeltaFiles();
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
const replayDialog = ref();
const resumeDialog = ref();
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
    label: "Replay",
    icon: "fas fa-sync fa-fw",
    visible: () => !beenReplayed.value && !beenDeleted.value && hasPermission("DeltaFileReplay"),
    command: () => {
      replayDialog.value.showConfirmDialog();
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
    label: "Pin",
    icon: "fas fa-thumb-tack fa-fw",
    visible: () => deltaFile.stage == "COMPLETE" && !deltaFile.pinned && hasPermission("DeltaFilePinning"),
    command: () => {
      onPin();
    },
  },
  {
    label: "Unpin",
    icon: "fas fa-thumb-tack fa-fw",
    visible: () => deltaFile.pinned && hasPermission("DeltaFilePinning"),
    command: () => {
      onUnpin();
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
      resumeDialog.value.showConfirmDialog();
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
  const classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const contentDeleted = computed(() => {
  return loaded.value && deltaFile.contentDeleted !== null;
});

const testMode = computed(() => {
  return loaded.value && _.some(deltaFile.flows, (flow) => flow.testMode);
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
  const response = await pendingAnnotations(did.value.toLowerCase());
  deltaFile["pendingAnnotations"] = response.data.pendingAnnotations.join(", ");
};

const menuItems = computed(() => {
  const items = staticMenuItems;
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

const canBeCancelled = computed(() => {
  return deltaFile.nextAutoResume !== null || !["COMPLETE", "ERROR", "CANCELLED"].includes(deltaFile.stage);
});

const pageHeader = computed(() => {
  const header = ["DeltaFile Viewer"];
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

const onPin = async () => {
  const response = await pin([did.value]);
  if (response[0].success) {
    loadDeltaFileData();
  } else {
    notify.error("Failed to pin DeltaFile", response[0].info);
  }
};

const onUnpin = async () => {
  const response = await unpin([did.value]);
  if (response[0].success) {
    loadDeltaFileData();
  } else {
    notify.error("Failed to unpin DeltaFile", response[0].info);
  }
};

const latestError = (deltaFile) => {
  return _.chain(deltaFile.flows)
    .map((flow) => flow.actions)
    .flatten()
    .filter((action) => action.state === "ERROR")
    .sortBy(["modified"])
    .value()[0];
};

const autoResumeSelected = computed(() => {
  const newResumeRule = {};
  if (!_.isEmpty(deltaFile) && isError.value) {
    const rowInfo = JSON.parse(JSON.stringify(deltaFile));
    const errorInfo = latestError(rowInfo);
    newResumeRule["dataSource"] = rowInfo.dataSource;
    newResumeRule["action"] = errorInfo.name;
    newResumeRule["errorSubstring"] = errorInfo.errorCause;
    return newResumeRule;
  } else {
    return {};
  }
});
</script>

<style>
.deltafile-viewer-page {
  .row {
    .links-panel {
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
