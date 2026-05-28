<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: Transform builder page for creating and editing transform flow plans. -->
<!-- ABOUTME: Provides a visual graph editor for subscribe topics, actions, and publish rules. -->

<template>
  <div ref="transformBuilderPage" class="transform-builder-page">
    <PageHeader>
      <template #header>
        <h2 class="mb-0">Transform Builder</h2>
      </template>
    </PageHeader>
    <div v-if="model.active">
      <!-- Transform Flow Graph -->
      <div class="row p-2">
        <div class="col pl-2 pr-1">
          <Panel class="table-panel transform-graph-panel">
            <template #header>
              <div class="transform-panel-header">
                <span class="transform-name">{{ model.name }}</span>
                <span v-if="model.description" class="transform-description">{{ model.description }}</span>
                <DialogTemplate component-name="transformBuilder/TransformConfigurationDialog" :header="`Edit ${model.name}`" dialog-width="25vw" model-position="center" :data-prop="model" :edit-transform="editExistingTransform" @create-transform="setTransformValues">
                  <Button v-tooltip.top="`Edit`" icon="pi pi-pencil" severity="secondary" text size="small" rounded />
                </DialogTemplate>
              </div>
            </template>
            <TransformFlowGraph
              :subscribe-topics="model['subscribe'] || []"
              :actions="transformActions"
              :publish-rules="model['publish']?.rules || []"
              :publish-settings="model['publish']"
              :selected-node-id="selectedNodeId"
              :action-errors="actionErrorsMap"
              :subscribe-error="validateSubscribe"
              :publish-error="validatePublish"
              @node-click="handleNodeClick"
              @add-action="handleAddAction"
              @add-subscribe="handleAddSubscribe"
              @add-publish="handleAddPublish"
              @reorder-action="reorderAction"
              @reorder-subscribe="reorderSubscribe"
              @reorder-publish="reorderPublish"
              @delete-action="deleteActionByIndex"
              @delete-subscribe="deleteSubscribeByIndex"
              @delete-publish="deletePublishByIndex"
              @open-publish-settings="publishSettingsVisible = true"
              @update:selected-node-id="selectedNodeId = $event"
            />
          </Panel>
        </div>
      </div>
    </div>
    <HoverSaveButton v-if="model.active" target="window" :model="items" />
    <!-- Action Picker Dialog -->
    <Dialog v-model:visible="actionPickerVisible" header="Add Action" :modal="true" :style="{ width: '1200px', maxWidth: '95vw' }" :dismissable-mask="true">
      <div class="action-picker-content">
        <div class="action-picker-search mb-3">
          <IconField iconPosition="left" class="w-full">
            <InputIcon class="pi pi-search" />
            <InputText v-model="actionSearchQuery" class="w-full" placeholder="Search actions..." aria-label="Search actions" autofocus />
          </IconField>
        </div>
        <table class="action-picker-table">
          <thead>
            <tr>
              <th class="col-action">Action</th>
              <th class="col-plugin">Plugin</th>
              <th class="col-description">Description</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="action in filteredActions" :key="action.id">
              <tr
                class="action-picker-row"
                :class="{ selected: selectedPickerAction?.id === action.id }"
                @click="selectPickerAction(action)"
                @dblclick="confirmAddAction(action)"
              >
                <td class="col-action">{{ action.displayName }}</td>
                <td class="col-plugin">{{ action.pluginCoordinate?.artifactId }}</td>
                <td class="col-description">
                  <div
                    v-if="action.description"
                    class="action-description-preview"
                    :class="{ expanded: selectedPickerAction?.id === action.id && shouldExpandActionDescription(action.description) }"
                  >
                    <span
                      v-if="shouldExpandActionDescription(action.description) && selectedPickerAction?.id === action.id"
                      class="action-description-more"
                    >
                      &lt;click for less&gt;
                    </span>
                    <span
                      v-if="selectedPickerAction?.id !== action.id || !shouldExpandActionDescription(action.description)"
                      class="action-description-text"
                    >
                      {{ action.description }}
                    </span>
                    <div
                      v-else
                      class="action-description-expanded markdown-content"
                      v-html="renderActionDescription(action.description)"
                    />
                    <span
                      v-if="shouldExpandActionDescription(action.description) && selectedPickerAction?.id !== action.id"
                      class="action-description-more"
                    >
                      &lt;click for more&gt;
                    </span>
                  </div>
                  <span v-else class="no-description">—</span>
                </td>
              </tr>
            </template>
            <tr v-if="filteredActions.length === 0">
              <td colspan="3" class="action-picker-empty">No actions match your search</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" @click="actionPickerVisible = false" />
        <Button label="Add" :disabled="!selectedPickerAction" @click="confirmAddAction(selectedPickerAction)" />
      </template>
    </Dialog>

    <!-- Action Editor Dialog -->
    <Dialog v-model:visible="actionEditorVisible" :header="`Edit Action: ${editingAction?.name || editingAction?.displayName || ''}`" :modal="true" :style="{ width: '600px' }" :dismissable-mask="true">
      <div v-if="editingAction" class="action-editor-content">
        <div class="field">
          <label for="action-name-input">Name<span class="required-indicator" aria-hidden="true">*</span></label>
          <InputText id="action-name-input" v-model="editingAction.name" class="w-full" aria-required="true" placeholder="Action Name Required" />
        </div>
        <div class="field">
          <!-- eslint-disable-next-line vuejs-accessibility/label-has-for -- labels a non-input display element via aria-labelledby -->
          <label id="action-type-label">Type</label>
          <div class="action-type-display" aria-labelledby="action-type-label">{{ editingAction.type }}</div>
        </div>
        <div v-if="editingAction.schema && !_.isEmpty(editingAction.schema.properties)" class="field">
          <!-- eslint-disable-next-line vuejs-accessibility/label-has-for -- labels a JsonForms component group -->
          <label id="action-params-label">Parameters</label>
          <JsonForms
            :data="editingAction.parameters || {}"
            :renderers="renderers"
            :schema="editingAction.schema"
            :config="formsConfig"
            :ajv="handleDefaultsAjv"
            @change="onEditingActionParamsChange"
          />
        </div>
      </div>
      <template #footer>
        <Button label="Delete" severity="danger" @click="deleteEditingAction" />
        <Button label="Save" @click="actionEditorVisible = false" />
      </template>
    </Dialog>

    <!-- Subscribe Topic Editor Dialog -->
    <Dialog v-model:visible="subscribeEditorVisible" :header="isAddingSubscribe ? 'Add Subscribe Topic' : 'Edit Subscribe Topic'" :modal="true" :style="{ width: '500px' }" :dismissable-mask="true" @hide="cancelSubscribeEdit">
      <div v-if="editingSubscribeTopic" class="topic-editor-content">
        <div class="field">
          <label for="subscribe-topic-input">Topic</label>
          <Dropdown id="subscribe-topic-input" v-model="editingSubscribeTopic.topic" :options="allTopics" placeholder="Select a topic or type a new name" class="w-full" editable />
        </div>
        <div class="field">
          <label for="subscribe-condition-input">Condition (Optional)</label>
          <SpelEditorDialog id="subscribe-condition-input" v-model="editingSubscribeTopic.condition" mode="CONDITION" empty-placeholder="No condition" />
        </div>
      </div>
      <template #footer>
        <Button v-if="!isAddingSubscribe" label="Delete" severity="danger" @click="deleteEditingSubscribe" />
        <Button label="Cancel" severity="secondary" @click="cancelSubscribeEdit" />
        <Button :label="isAddingSubscribe ? 'Add' : 'Done'" :disabled="!editingSubscribeTopic?.topic" @click="submitSubscribeTopic" />
      </template>
    </Dialog>

    <!-- Publish Topic Editor Dialog -->
    <Dialog v-model:visible="publishEditorVisible" :header="isAddingPublish ? 'Add Publish Topic' : 'Edit Publish Topic'" :modal="true" :style="{ width: '500px' }" :dismissable-mask="true" @hide="cancelPublishEdit">
      <div v-if="editingPublishTopic" class="topic-editor-content">
        <div class="field">
          <label for="publish-topic-input">Topic</label>
          <Dropdown id="publish-topic-input" v-model="editingPublishTopic.topic" :options="allTopics" placeholder="Select a topic or type a new name" class="w-full" editable />
        </div>
        <div class="field">
          <label for="publish-condition-input">Condition (Optional)</label>
          <SpelEditorDialog id="publish-condition-input" v-model="editingPublishTopic.condition" mode="CONDITION" empty-placeholder="No condition" />
        </div>
      </div>
      <template #footer>
        <Button v-if="!isAddingPublish" label="Delete" severity="danger" @click="deleteEditingPublish" />
        <Button label="Cancel" severity="secondary" @click="cancelPublishEdit" />
        <Button :label="isAddingPublish ? 'Add' : 'Done'" :disabled="!editingPublishTopic?.topic" @click="submitPublishTopic" />
      </template>
    </Dialog>

    <!-- Publish Settings Dialog -->
    <Dialog v-model:visible="publishSettingsVisible" header="Publish Settings" :modal="true" :style="{ width: '500px' }" :dismissable-mask="true">
      <div v-if="model['publish']" class="publish-settings-content">
        <div class="field">
          <label for="matching-policy-input">If multiple topics would receive data:</label>
          <Dropdown id="matching-policy-input" v-model="model['publish'].matchingPolicy" :options="matchingPolicyOptions" option-label="label" option-value="value" class="w-full" />
        </div>
        <div class="field">
          <label for="default-behavior-input">If no topics would receive data:</label>
          <Dropdown id="default-behavior-input" v-model="model['publish'].defaultRule.defaultBehavior" :options="defaultBehaviorOptions" option-label="label" option-value="value" class="w-full" />
        </div>
        <div v-if="model['publish']?.defaultRule?.defaultBehavior === 'PUBLISH'" class="field">
          <label for="default-topic-input">Default publish topic:</label>
          <Dropdown id="default-topic-input" v-model="model['publish'].defaultRule.topic" :options="allTopics" placeholder="Select a topic" class="w-full" editable />
        </div>
      </div>
      <template #footer>
        <Button label="Close" @click="publishSettingsVisible = false" />
      </template>
    </Dialog>
    <DialogTemplate component-name="transformBuilder/TransformConfigurationDialog" header="Create New Transform" dialog-width="25vw" model-position="center" :closable="false" :disable-model="true" :data-prop="model" @create-transform="createFlowPlan">
      <span id="CreateFlowPlan" />
    </DialogTemplate>
    <LeavePageConfirmationDialog header="Leaving Transform Builder" message="There is a transform build in progress with unsaved changes. Leaving the page will erase those changes. Are you sure you want to leave this page?" :match-condition="transformInProgress()" />
    <Dialog v-model:visible="displayRawJsonDialog" :style="{ width: '90vw' }" modal maximizable close-on-escape dismissable-mask :draggable="false" header="Transform Raw Json" class="transform-raw-json-dialog" @hide="hideTransformRawJsonDialog">
      <Panel header="Output">
        <template #icons>
          <Button v-tooltip.left="'Show Schema'" severity="secondary" variant="text" class="p-panel-header-icon p-me-2" @click="showSchema()">
            <span class="fa-solid fa-file-invoice" />
          </Button>
          <Button v-tooltip.left="'Copy to Clipboard'" severity="secondary" variant="text" class="p-panel-header-icon p-me-2" @click="copy(rawOutput)">
            <span class="fa-solid fa-copy" />
          </Button>
        </template>
        <pre class="textAreaWidth" style="text-align: start; white-space: pre-wrap; overflow: auto; border-bottom: none" v-html="prettyPrint(rawOutput)" />
      </Panel>
    </Dialog>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import HoverSaveButton from "@/components/transformBuilder/HoverSaveButton.vue";
import LeavePageConfirmationDialog from "@/components/LeavePageConfirmationDialog.vue";
import PageHeader from "@/components/PageHeader.vue";
import TransformFlowGraph from "@/components/transformBuilder/TransformFlowGraph.vue";
import useFlowActions from "@/composables/useFlowActions";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import useTopics from "@/composables/useTopics";
import { compactSparseArray } from "@/utils/arrayUtils";
import { computed, nextTick, onBeforeMount, onMounted, onBeforeUnmount, provide, ref, watch } from "vue";
import { StorageSerializers, useClipboard, useMagicKeys, useResizeObserver, useStorage } from "@vueuse/core";
import { useRouter } from "vue-router";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";
import { createAjv } from "@jsonforms/core";
import MarkdownIt from "markdown-it";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Dropdown from "primevue/dropdown";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import SpelEditorDialog from "@/components/spel/SpelEditorDialog.vue";
import { extractSchemaDefaults } from "@/utils/schemaDefaults";

import _ from "lodash";

const markdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
});

const actionPickerVisible = ref(false);
const handleDefaultsAjv = createAjv({ useDefaults: true });
const { getAllTopicNames } = useTopics();
const { getAllFlows } = useFlowQueryBuilder();
const { getPluginActionSchema } = useFlowActions();
const { saveTransformFlowPlan } = useFlowPlanQueryBuilder();
const keys = useMagicKeys();
const devKey = keys["d+e+v"];
const { copy } = useClipboard();
const notify = useNotifications();
const router = useRouter();
const allTopics = ref(["default"]);

const matchingPolicyOptions = [
  { value: 'ALL_MATCHING', label: 'Publish to all matching topics' },
  { value: 'FIRST_MATCHING', label: 'Publish to only the first matching topic' },
];

const defaultBehaviorOptions = [
  { value: 'ERROR', label: 'Raise an error' },
  { value: 'FILTER', label: 'Filter (discard the data)' },
  { value: 'PUBLISH', label: 'Publish to a default topic' },
];

const { myStyles, rendererList } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));

const allActionsData = ref({});

const transformBuilderPage = ref(null);
const pageWidthResizeObserver = ref(null);
const editExistingTransform = ref(false);
const originalTransform = ref(null);

const schemaVisible = ref(false);
const displayRawJsonDialog = ref(false);

const allFlowPlanData = ref({});

const formsConfig = ref({ defaultLabels: true });

// Graph selection state
const selectedNodeId = ref(null);

// Dialog visibility state
const publishSettingsVisible = ref(false);
const actionEditorVisible = ref(false);
const subscribeEditorVisible = ref(false);
const publishEditorVisible = ref(false);

// Editing state for dialogs
const editingAction = ref(null);
const editingSubscribeTopic = ref(null);
const editingPublishTopic = ref(null);
const editingSubscribeIndex = ref(-1);
const editingPublishIndex = ref(-1);

// Track if we're adding (vs editing)
const isAddingSubscribe = ref(false);
const isAddingPublish = ref(false);

// The useResizeObserver determines if the sidebar has been collapsed or expanded.
// If either has occurred we redo the connections between all actions.
useResizeObserver(transformBuilderPage, (entries) => {
  const [entry] = entries;
  const { width } = entry.contentRect;
  pageWidthResizeObserver.value = width;
});

// This watch on key pressed state on "d+e+v" will activate the dialog to view the raw JSON of a Transform.
watch(devKey, (v) => {
  if (v) {
    if (model.value.active) {
      displayRawJsonDialog.value = !displayRawJsonDialog.value;
    }
  }
});

const linkedTransform = useStorage("linked-transform-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });


const defaultTopicTemplate = [{ condition: null, topic: null }];

const transformTemplate = {
  type: "TRANSFORM",
  active: false,
  name: null,
  description: null,
  subscribe: defaultTopicTemplate,
  publish: {
    rules: defaultTopicTemplate,
    matchingPolicy: "ALL_MATCHING",
    defaultRule: {
      defaultBehavior: "ERROR",
    },
  },
};

const defaultActionKeys = {
  name: null,
  type: null,
  disableEdit: false,
  description: null,
  parameters: {},
  apiVersion: null,
};

const transformActionsTemplate = {
  flowActionType: "TRANSFORM",
  ...defaultActionKeys,
  join: {},
};

const transformBlueprint = ref(JSON.parse(JSON.stringify(transformTemplate)));

const transformActions = ref([]);

// Check for refresh restoration before mount
const STORAGE_KEY = 'transformBuilderState';
const restoredFromRefresh = ref(false);

onBeforeMount(async () => {
  // Check if this is a refresh with saved state - do this first
  const navEntry = performance.getEntriesByType('navigation')[0];
  const isRefresh = navEntry?.type === 'reload';
  if (isRefresh) {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved) {
      try {
        const state = JSON.parse(saved);
        if (state.blueprint) {
          transformBlueprint.value = state.blueprint;
        }
        if (state.actions) {
          transformActions.value = state.actions;
        }
        restoredFromRefresh.value = true;
        sessionStorage.removeItem(STORAGE_KEY);
      } catch (e) {
        console.warn('Failed to restore transform builder state:', e);
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  }

  const topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  const responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  getLoadedActions();

  const response = await getAllFlows();
  allFlowPlanData.value = response.data.getAllFlows;

  // Skip dialog if we restored from refresh
  if (restoredFromRefresh.value) {
    return;
  }

  if (!_.isEmpty(_.get(linkedTransform.value, "transformParams", null))) {
    if (linkedTransform.value.transformParams.editExistingTransform) {
      editExistingTransform.value = true;
      const transformInfo = {};
      transformInfo["type"] = _.toUpper(linkedTransform.value.transformParams.type);
      transformInfo["name"] = linkedTransform.value.transformParams.selectedTransformName;
      transformInfo["selectedTransform"] = _.find(allFlowPlanData.value[`${_.toLower(linkedTransform.value.transformParams.type)}`], { name: linkedTransform.value.transformParams.selectedTransformName });
      transformInfo["description"] = transformInfo["selectedTransform"].description;
      if (_.has(linkedTransform.value.transformParams.selectedTransform, "subscribe")) {
        transformInfo["subscribe"] = linkedTransform.value.transformParams.selectedTransform.subscribe || [];
      }
      if (_.has(linkedTransform.value.transformParams.selectedTransform, "publish")) {
        transformInfo["publish"] = linkedTransform.value.transformParams.selectedTransform.publish || {};
      }
      await createFlowPlan(transformInfo);
      originalTransform.value = rawOutput.value;
    } else {
      model.value.type = _.toUpper(linkedTransform.value.transformParams.type);
      model.value.selectedTransform = _.find(allFlowPlanData.value[`${_.toLower(linkedTransform.value.transformParams.type)}`], { name: linkedTransform.value.transformParams.selectedTransformName });
      document.getElementById("CreateFlowPlan").click();
    }
    linkedTransform.value = null;
  } else {
    document.getElementById("CreateFlowPlan").click();
  }
});

// Save state for F5 refresh
const saveStateForRefresh = () => {
  const state = {
    blueprint: transformBlueprint.value,
    actions: transformActions.value,
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
};

const handleBeforeUnload = () => {
  // Only save if there's actual work in progress
  if (transformBlueprint.value?.name || transformActions.value.length > 0) {
    saveStateForRefresh();
  }
};

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload);
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload);
  sessionStorage.removeItem(STORAGE_KEY);
});

const model = computed({
  get() {
    return new Proxy(transformBlueprint.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      transformBlueprint.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

// This watch on publish.defaultRule.defaultBehavior if the value is not "PUBLISH"
// will delete the publish.defaultRule.topic key from the model.publish.defaultRule
watch(
  () => model.value?.publish?.defaultRule,
  () => {
    if (!_.isEqual(model.value?.publish?.defaultRule.defaultBehavior, "PUBLISH")) {
      delete model.value.publish.defaultRule.topic;
    }
  }
);


const createFlowPlan = async (newFlowPlan) => {
  removeFlow();
  await setTransformValues(newFlowPlan);
  if (newFlowPlan.selectedTransform) {
    cloneTransform(newFlowPlan);
  }
};

const setTransformValues = async (transformInfo) => {
  await nextTick();
  model.value.type = transformInfo["type"];
  model.value.name = transformInfo["name"];
  model.value.description = transformInfo["description"];
  model.value.selectedTransform = transformInfo["selectedTransform"];

  if (_.has(transformInfo["selectedTransform"], "subscribe")) {
    model.value["subscribe"] = transformInfo["selectedTransform"].subscribe || defaultTopicTemplate;
  }

  if (_.has(transformInfo["selectedTransform"], "publish")) {
    model.value["publish"] = transformInfo["selectedTransform"].publish || {};
  }
  model.value.active = true;
};

const cloneTransform = async (cloneTransform) => {
  let clonedTransformActions = [];

  const getClonedActionsByTypes = _.cloneDeep(_.get(cloneTransform.selectedTransform, "transformActions"));
  if (!_.isEmpty(getClonedActionsByTypes)) {
    clonedTransformActions = clonedTransformActions.concat(getClonedActionsByTypes);
    if (!_.isEmpty(clonedTransformActions)) {
      for (const clonedAction of clonedTransformActions) {
        const tmpMergedActionAndActionSchema = _.cloneDeep(_.find(flattenedActions.value, { type: clonedAction.type, flowActionType: "TRANSFORM" }));
        const mergedActionAndActionSchema = _.merge(tmpMergedActionAndActionSchema, clonedAction);
        addAction(mergedActionAndActionSchema);
      }
    }
  }
};

const removeEmptyKeyValues = (queryObj) => {
  const newObj = {};
  Object.entries(queryObj).forEach(([k, v]) => {
    if (v instanceof Array) {
      newObj[k] = queryObj[k];
    } else if (v === Object(v)) {
      newObj[k] = removeEmptyKeyValues(v);
    } else if (v != null) {
      newObj[k] = queryObj[k];
    }
  });
  return newObj;
};

const clearEmptyObjects = (queryObj) => {
  for (const objKey in queryObj) {
    if (_.isArray(queryObj[objKey])) {
      if (_.some(queryObj[objKey], _.isNil)) {
        queryObj[objKey].forEach(function (item, index) {
          queryObj[objKey][index] = removeEmptyKeyValues(item);
        });
      }

      if (queryObj[objKey].every((value) => typeof value === "object")) {
        queryObj[objKey] = queryObj[objKey].filter((value) => Object.keys(value).length !== 0);
      }

      if (_.isEmpty(queryObj[objKey])) {
        delete queryObj[objKey];
        continue;
      }

      if (Object.keys(queryObj[objKey]).length === 0) {
        delete queryObj[objKey];
      }
    }

    if (_.isObject(queryObj[objKey])) {
      clearEmptyObjects(queryObj[objKey]);
    }

    if (_.isEmpty(queryObj[objKey]) && !_.isBoolean(queryObj[objKey]) && !_.isNumber(queryObj[objKey])) {
      delete queryObj[objKey];
    }
  }

  if (_.isArray(queryObj)) compactSparseArray(queryObj);

  return queryObj;
};

const save = async (rawTransform) => {
  let response = null;
  let newRawTransform = JSON.parse(JSON.stringify(rawTransform));
  newRawTransform = clearEmptyObjects(newRawTransform);
  response = await saveTransformFlowPlan(newRawTransform);
  if (response !== undefined) {
    notify.success(`${response.data[`save${_.capitalize(model.value.type)}FlowPlan`].name} Transform Saved`);
    model.value.active = false;
    // Null out the originalTransform value as that Transform has changed.
    originalTransform.value = null;
    router.push({ path: `/config/transforms` });
  }
};

const removeFlow = () => {
  model.value = JSON.parse(JSON.stringify(transformTemplate));
  transformActions.value = [];
};

const addAction = async (action) => {
  const addNewAction = JSON.parse(JSON.stringify(action));
  addNewAction["id"] = _.uniqueId("transformActions");

  // Insert at pending index if set, otherwise append
  if (pendingInsertIndex.value !== null) {
    transformActions.value.splice(pendingInsertIndex.value, 0, addNewAction);
    pendingInsertIndex.value = null;
  } else {
    transformActions.value.push(addNewAction);
  }

  return addNewAction;
};

const transformActionsChanged = () => {
  // Get all the keys from both original and rawOutput
  const allKeys = _.union(_.keys(originalTransform.value), _.keys(rawOutput.value));

  // Detect changes and deletions
  const changedTransformValues = _.omitBy(_.pick(rawOutput.value, allKeys), function (v, k) {
    return JSON.stringify(originalTransform.value[k]) === JSON.stringify(v);
  });

  // Detect deletions (keys present in originalTransform but missing in rawOutput)
  const deletedKeys = _.difference(_.keys(originalTransform.value), _.keys(rawOutput.value));

  // If there are no changes or deletions, return false
  if (_.isEmpty(changedTransformValues) && _.isEmpty(deletedKeys)) {
    return false;
  } else {
    return true;
  }
};

// Validates all parts of the Transform. If invalid it disables the save button.
const isValidTransform = computed(() => {
  // If the Transform Raw Dialog or the Schema is visible disable the save
  if (schemaVisible.value || displayRawJsonDialog.value) {
    return false;
  }

  // If we are editing an existing Transform and we haven't made any changes disable the save
  if (!_.isEmpty(originalTransform.value)) {
    // If there are no changes or deletions, return false
    if (!transformActionsChanged()) {
      return false;
    }
  }

  // If the Transform isn't active disable the save
  if (!model.value.active) {
    return false;
  }

  if (!_.isEmpty(validateSubscribe.value)) {
    return false;
  }

  if (!_.isEmpty(validatePublish.value)) {
    return false;
  }

  // If there are missing required fields disable the save
  const allTransformMissingFields = [];
  for (const action of transformActions.value) {
    const actionMissingRequiredFields = validateAction(action);
    if (!_.isEmpty(actionMissingRequiredFields)) {
      allTransformMissingFields.push(actionMissingRequiredFields);
    }
  }

  return allTransformMissingFields.length == 0;
});

const transformInProgress = () => {
  // If the transform is being edited and a value is changed return true that the transform should be saved
  if (!_.isEmpty(originalTransform.value)) {
    return transformActionsChanged();
  }

  return model.value.active;
};

const saveDisabledReason = computed(() => {
  const reasons = [];
  if (!_.isEmpty(validateSubscribe.value)) {
    reasons.push(validateSubscribe.value);
  }
  if (!_.isEmpty(validatePublish.value)) {
    reasons.push(validatePublish.value);
  }
  for (const action of transformActions.value) {
    const error = validateAction(action);
    if (error) {
      reasons.push(`${displayActionName(action)}: ${error}`);
    }
  }
  if (reasons.length === 0 && !isValidTransform.value) {
    reasons.push("No changes to save");
  }
  return reasons.join('\n');
});

const items = ref([
  {
    label: "Save",
    icon: "fa-solid fa-hard-drive",
    isEnabled: isValidTransform,
    disabledLabel: saveDisabledReason,
    visible: true,
    command: () => {
      save(rawOutput.value);
    },
  },
]);

const validateSubscribe = computed(() => {
  if (_.isEmpty(model.value["subscribe"])) {
    return "Subscribe to at least one topic";
  }

  const checkIfSubscribeHasTopic = (key) =>
    model.value["subscribe"].some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  const isKeyPresent = checkIfSubscribeHasTopic("topic");
  if (!isKeyPresent) {
    return "Subscribe to at least one topic";
  }

  return null;
});

const validatePublish = computed(() => {
  if (_.isEqual(model.value["publish"]?.defaultRule?.defaultBehavior, "PUBLISH") && _.isEmpty(model.value["publish"]?.defaultRule?.topic)) {
    return "Default behavior requires a topic";
  }

  if (_.isEmpty(model.value["publish"].rules)) {
    return "Publish to at least one topic";
  }

  const checkIfPublishRulesHasTopic = (key) =>
    model.value["publish"].rules.some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  const isKeyPresent = checkIfPublishRulesHasTopic("topic");

  if (!isKeyPresent) {
    return "Publish to at least one topic";
  }

  return null;
});

const validateAction = (action) => {
  // List of all missing Fields in the action
  let missingFieldsInAction = [];
  // requiredSchemaFields is a list of all list of all the required fields for the action.
  const requiredSchemaFields = _.get(action.schema, "required", []);
  // completedFields is a list of all the keys of the fields that the user has filled in for the action.
  const completedFields = _.keys(_.get(action, "parameters", {}));

  // Check if the action name is missing if so add it to the list of missing required fields. The action name is required and should not allow the transform to be saved.
  if (_.isEmpty(action.name)) {
    missingFieldsInAction.push("name");
  }

  // Check if there are required schema fields, if so get the list of incomplete fields by comparing the required schema fields
  // with the keys of the user completed fields and add those to the list of missing required fields.
  missingFieldsInAction = _.concat(missingFieldsInAction, _.difference(requiredSchemaFields, completedFields));

  // All action names within a Transform have to be unique.
  let duplicateActionNames = "";
  if (!_.isEmpty(action.name)) {
    const duplicateActionNamesInTransform = _.filter(_.flatten(transformActions.value), { name: action.name });
    if (duplicateActionNamesInTransform.length > 1) {
      duplicateActionNames = `All action names within a Transform have to be unique. Duplicate action name: ${action.name}.`;
    }
  }

  if (_.isEmpty(missingFieldsInAction) && _.isEmpty(duplicateActionNames)) {
    return null;
  }

  const invalidActionFields = `${_.isEmpty(duplicateActionNames) ? "" : duplicateActionNames} ${!_.isEmpty(duplicateActionNames) && !_.isEmpty(missingFieldsInAction) ? " and " : ""} ${_.isEmpty(missingFieldsInAction) ? "" : `missing required fields: ${missingFieldsInAction.join(", ")}`}`;

  return _.capitalize(invalidActionFields.trim());
};

const displayActionName = (action) => {
  if (_.isEmpty(action.name)) {
    return action.displayName;
  }

  return action.name;
};

const actionErrorsMap = computed(() => {
  const errors = new Map();
  transformActions.value.forEach(action => {
    const error = validateAction(action);
    if (error) {
      errors.set(action.id, error);
    }
  });
  return errors;
});

// Graph event handlers
const handleNodeClick = (node) => {
  selectedNodeId.value = node.id;

  // Open the appropriate edit dialog based on node type
  if (node.nodeType === 'action') {
    const actionId = node.id.replace('action:', '');
    const action = transformActions.value.find(a => a.id === actionId);
    if (action) {
      editingAction.value = action;
      actionEditorVisible.value = true;
    }
  } else if (node.nodeType === 'subscribe') {
    const index = parseInt(node.id.replace('subscribe:', ''), 10);
    if (model.value['subscribe'] && index >= 0 && index < model.value['subscribe'].length) {
      isAddingSubscribe.value = false;
      editingSubscribeIndex.value = index;
      editingSubscribeTopic.value = { ...model.value['subscribe'][index] };
      subscribeEditorVisible.value = true;
    }
  } else if (node.nodeType === 'publish') {
    const index = parseInt(node.id.replace('publish:', ''), 10);
    if (model.value['publish']?.rules && index >= 0 && index < model.value['publish'].rules.length) {
      isAddingPublish.value = false;
      editingPublishIndex.value = index;
      editingPublishTopic.value = { ...model.value['publish'].rules[index] };
      publishEditorVisible.value = true;
    }
  }
};

const handleAddAction = (insertIndex) => {
  pendingInsertIndex.value = insertIndex;
  actionSearchQuery.value = '';
  selectedPickerAction.value = null;
  actionPickerVisible.value = true;
};

const handleAddSubscribe = () => {
  // Open add dialog with empty topic (don't add until confirmed)
  isAddingSubscribe.value = true;
  editingSubscribeTopic.value = { topic: null, condition: null };
  editingSubscribeIndex.value = -1;
  subscribeEditorVisible.value = true;
};

const handleAddPublish = () => {
  // Open add dialog with empty topic (don't add until confirmed)
  isAddingPublish.value = true;
  editingPublishTopic.value = { topic: null, condition: null };
  editingPublishIndex.value = -1;
  publishEditorVisible.value = true;
};

const pendingInsertIndex = ref(null);

// Action picker state
const actionSearchQuery = ref('');
const selectedPickerAction = ref(null);
const flattenedActions = ref([]);

const filteredActions = computed(() => {
  const query = actionSearchQuery.value.toLowerCase();
  const matchingActions = !query
    ? flattenedActions.value
    : flattenedActions.value.filter(action => {
        return action.displayName?.toLowerCase().includes(query) ||
               action.type?.toLowerCase().includes(query) ||
               action.pluginCoordinate?.groupId?.toLowerCase().includes(query) ||
               action.description?.toLowerCase().includes(query);
      });

  return [...matchingActions].sort((left, right) =>
    (left.displayName || "").localeCompare(right.displayName || "")
  );
});

const renderActionDescription = (description) => markdownIt.render(description || "");

const shouldExpandActionDescription = (description) => {
  if (!description) {
    return false;
  }

  return description.length > 140 || /(^|\n)\s*(#{1,6}\s|[-*+]\s|\d+\.\s|> )|`{1,3}|\[.+?\]\(.+?\)|\n/.test(description);
};

const selectPickerAction = (action) => {
  if (selectedPickerAction.value?.id === action.id) {
    selectedPickerAction.value = null;
    return;
  }

  selectedPickerAction.value = action;
};

const confirmAddAction = async (action) => {
  if (!action) return;
  const clonedAction = cloneAction(action);
  const addedAction = await addAction(clonedAction);
  actionPickerVisible.value = false;
  selectedNodeId.value = `action:${addedAction.id}`;
  nextTick(() => {
    editingAction.value = transformActions.value.find(a => a.id === addedAction.id);
    actionEditorVisible.value = true;
  });
};

const reorderAction = (fromIndex, toIndex) => {
  if (fromIndex < 0 || fromIndex >= transformActions.value.length) return;
  if (toIndex < 0 || toIndex >= transformActions.value.length) return;
  if (fromIndex === toIndex) return;

  const action = transformActions.value.splice(fromIndex, 1)[0];
  transformActions.value.splice(toIndex, 0, action);
};

const deleteActionByIndex = (index) => {
  if (index < 0 || index >= transformActions.value.length) return;

  // Clear selection if we're deleting the selected action
  const action = transformActions.value[index];
  if (selectedNodeId.value === `action:${action.id}`) {
    selectedNodeId.value = null;
  }

  transformActions.value.splice(index, 1);
};

const reorderSubscribe = (fromIndex, toIndex) => {
  const subs = model.value['subscribe'];
  if (!subs || fromIndex < 0 || fromIndex >= subs.length) return;
  if (toIndex < 0 || toIndex >= subs.length) return;
  if (fromIndex === toIndex) return;

  const item = subs.splice(fromIndex, 1)[0];
  subs.splice(toIndex, 0, item);
};

const deleteSubscribeByIndex = (index) => {
  const subs = model.value['subscribe'];
  if (!subs || index < 0 || index >= subs.length) return;

  if (selectedNodeId.value === `subscribe:${index}`) {
    selectedNodeId.value = null;
  }

  subs.splice(index, 1);
};

const reorderPublish = (fromIndex, toIndex) => {
  const rules = model.value['publish']?.rules;
  if (!rules || fromIndex < 0 || fromIndex >= rules.length) return;
  if (toIndex < 0 || toIndex >= rules.length) return;
  if (fromIndex === toIndex) return;

  const item = rules.splice(fromIndex, 1)[0];
  rules.splice(toIndex, 0, item);
};

const deletePublishByIndex = (index) => {
  const rules = model.value['publish']?.rules;
  if (!rules || index < 0 || index >= rules.length) return;

  if (selectedNodeId.value === `publish:${index}`) {
    selectedNodeId.value = null;
  }

  rules.splice(index, 1);
};

// Dialog handler functions
const onEditingActionParamsChange = (event) => {
  if (editingAction.value) {
    editingAction.value.parameters = event.data;
  }
};

const deleteEditingAction = () => {
  if (editingAction.value) {
    const index = transformActions.value.findIndex(a => a.id === editingAction.value.id);
    if (index >= 0) {
      transformActions.value.splice(index, 1);
    }
    editingAction.value = null;
    actionEditorVisible.value = false;
    selectedNodeId.value = null;
  }
};

const deleteEditingSubscribe = () => {
  if (editingSubscribeIndex.value >= 0 && model.value['subscribe']) {
    model.value['subscribe'].splice(editingSubscribeIndex.value, 1);
    editingSubscribeTopic.value = null;
    editingSubscribeIndex.value = -1;
    subscribeEditorVisible.value = false;
    selectedNodeId.value = null;
  }
};

const deleteEditingPublish = () => {
  if (editingPublishIndex.value >= 0 && model.value['publish']?.rules) {
    model.value['publish'].rules.splice(editingPublishIndex.value, 1);
    editingPublishTopic.value = null;
    editingPublishIndex.value = -1;
    publishEditorVisible.value = false;
    selectedNodeId.value = null;
  }
};

const submitSubscribeTopic = () => {
  if (!editingSubscribeTopic.value?.topic) return;
  if (isAddingSubscribe.value) {
    if (!model.value['subscribe']) {
      model.value['subscribe'] = [];
    }
    model.value['subscribe'].push({ ...editingSubscribeTopic.value });
  } else if (editingSubscribeIndex.value >= 0 && model.value['subscribe']) {
    Object.assign(model.value['subscribe'][editingSubscribeIndex.value], editingSubscribeTopic.value);
  }
  subscribeEditorVisible.value = false;
  isAddingSubscribe.value = false;
  editingSubscribeTopic.value = null;
  editingSubscribeIndex.value = -1;
};

const submitPublishTopic = () => {
  if (!editingPublishTopic.value?.topic) return;
  if (isAddingPublish.value) {
    if (!model.value['publish']) {
      model.value['publish'] = { rules: [], matchingPolicy: 'ALL_MATCHING', defaultRule: { defaultBehavior: 'ERROR' } };
    }
    if (!model.value['publish'].rules) {
      model.value['publish'].rules = [];
    }
    model.value['publish'].rules.push({ ...editingPublishTopic.value });
  } else if (editingPublishIndex.value >= 0 && model.value['publish']?.rules) {
    Object.assign(model.value['publish'].rules[editingPublishIndex.value], editingPublishTopic.value);
  }
  publishEditorVisible.value = false;
  isAddingPublish.value = false;
  editingPublishTopic.value = null;
  editingPublishIndex.value = -1;
};

const cancelSubscribeEdit = () => {
  subscribeEditorVisible.value = false;
  isAddingSubscribe.value = false;
  editingSubscribeTopic.value = null;
  editingSubscribeIndex.value = -1;
};

const cancelPublishEdit = () => {
  publishEditorVisible.value = false;
  isAddingPublish.value = false;
  editingPublishTopic.value = null;
  editingPublishIndex.value = -1;
};

const cloneAction = (clonedAction) => {
  const addNewClonedAction = JSON.parse(JSON.stringify(clonedAction));
  addNewClonedAction["id"] = _.uniqueId(addNewClonedAction["flowActionType"]);
  addNewClonedAction["name"] = generateUniqueActionName(addNewClonedAction["displayName"]);
  return JSON.parse(JSON.stringify(addNewClonedAction));
};

const generateUniqueActionName = (baseName) => {
  const existingNames = transformActions.value.map((a) => a.name);
  if (!existingNames.includes(baseName)) {
    return baseName;
  }
  let suffix = 2;
  while (existingNames.includes(`${baseName}${suffix}`)) {
    suffix++;
  }
  return `${baseName}${suffix}`;
};

const getLoadedActions = () => {
  for (const plugin of allActionsData.value) {
    for (const action of plugin["actions"]) {
      if (!_.isEqual(action.type, "TRANSFORM")) continue;

      // Reformatting each action.
      action["disableEdit"] = true;
      action["flowActionType"] = action["type"];
      action["type"] = action["name"];
      const displayName = action.name.split(".").pop();
      action["displayName"] = displayName;
      action["id"] = _.uniqueId(`${displayName}_`);
      const coordinateGrouping = action.name.split(".").slice(0, -1).join(".");
      action["coordinateGrouping"] = coordinateGrouping;
      action["pluginCoordinate"] = plugin.pluginCoordinates;
      action["name"] = "";

      // Copy description from actionOptions if available
      action["description"] = action.actionOptions?.description || action.docsMarkdown || null;

      action["parameters"] = extractSchemaDefaults(action.schema);

      action["join"] = {};
      flattenedActions.value.push(action);
    }
  }
};

const rawOutput = computed(() => {
  if (!model.value.active) {
    return {};
  }
  let displayOutput = JSON.parse(JSON.stringify(model.value));

  if (!_.isEmpty(transformActions.value)) {
    displayOutput["transformActions"] = JSON.parse(JSON.stringify(transformActions.value));
    if (schemaVisible.value) {
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs);
    } else {
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ ...attrs }) => _.pick(attrs, Object.keys([transformActionsTemplate][0])));
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ schema, description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs);
    }
  }

  // Remove UI metakeys from output
  displayOutput = _.omit(displayOutput, ["active", "selectedTransform"]);

  return displayOutput;
});

const showSchema = () => {
  schemaVisible.value = !schemaVisible.value;
};

const hideTransformRawJsonDialog = () => {
  if (schemaVisible.value) {
    schemaVisible.value = !schemaVisible.value;
  }
};

const prettyPrint = (json) => {
  if (json) {
    const stringified = JSON.stringify(json, null, 2);

    const stringifiedReplaced = stringified.replace(/&/g, "&").replace(/</g, "<").replace(/>/g, ">");
    const regex = /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g;

    const test = stringifiedReplaced.replace(regex, (match) => {
      let className = "number";
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          className = "key";
        } else {
          className = "string";
        }
      } else if (/true|false/.test(match)) {
        className = "boolean";
      } else if (/null/.test(match)) {
        className = "null";
      }

      return `<span class="${className}">${match}</span>`;
    });

    return test;
  }

  return "";
};

const onSubscribeChange = (event) => {
  model.value["subscribe"] = event.data;
};

const subscribeSchema = {
  type: "array",
  title: "Topics",
  items: {
    type: "object",
    properties: {
      topic: {
        type: "string",
        title: "Topic Name",
        enum: allTopics.value,
      },
      condition: {
        title: "Condition (Optional)",
        type: "string",
      },
    },
  },
};

const onPublishChange = (event) => {
  model.value["publish"] = event.data;
};

const publishSchema = {
  type: "object",
  properties: {
    rules: {
      title: "Topics",
      type: "array",
      items: {
        type: "object",
        properties: {
          topic: {
            type: "string",
            title: "Topic Name",
            enum: allTopics.value,
          },
          condition: {
            title: "Condition (Optional)",
            type: "string",
          },
        },
      },
    },
    matchingPolicy: {
      title: "If multiple topics would receive data:",
      type: "string",
      enum: ["ALL_MATCHING", "FIRST_MATCHING"],
      default: "ALL_MATCHING",
    },
    defaultRule: {
      type: "object",
      default: {},
      properties: {
        defaultBehavior: {
          title: "If no topics would receive data:",
          type: "string",
          enum: ["ERROR", "FILTER", "PUBLISH"],
          default: "ERROR",
        },
        topic: {
          type: "string",
          title: "Topic Name",
          enum: allTopics.value,
        },
      },
    },
  },
};

const publishUISchema = {
  type: "VerticalLayout",
  elements: [
    {
      type: "Control",
      scope: "#/properties/rules",
      options: {
        detail: {
          type: "VerticalLayout",
          elements: [
            {
              type: "Control",
              scope: "#/properties/topic",
            },
            {
              type: "Control",
              scope: "#/properties/condition",
            },
          ],
        },
      },
    },
    {
      rule: {
        effect: "HIDE",
        condition: {
          scope: "#/properties/rules",
          schema: { minItems: 0, maxItems: 1 },
        },
      },
      elements: [
        {
          type: "Control",
          title: "If Multiple topics would receive data:",
          scope: "#/properties/matchingPolicy",
        },
      ],
    },
    {
      type: "Control",
      scope: "#/properties/defaultRule",
      options: {
        detail: {
          type: "Group",
          elements: [
            {
              type: "Control",
              scope: "#/properties/defaultBehavior",
            },
            {
              type: "Control",
              scope: "#/properties/topic",
              rule: {
                effect: "HIDE",
                condition: {
                  scope: "#/properties/defaultBehavior",
                  schema: { enum: ["ERROR", "FILTER"] },
                },
              },
            },
          ],
        },
      },
    },
  ],
};
</script>

<style>
.transform-builder-page {
  .p-panel .p-panel-header {
    height: 44px !important;
  }

  /* Selected action editor styles */
  .selected-action-panel {
    border: 2px solid var(--primary-color);
  }

  .action-editor-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1rem;
  }

  .action-editor-field {
    label {
      display: block;
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--df-body-color);
      margin-bottom: 0.25rem;
    }
  }

  .action-type-display {
    font-family: monospace;
    font-size: 0.85rem;
    color: var(--text-color-secondary);
    padding: 0.5rem;
    background: var(--df-subtle-bg-alt);
    border-radius: 4px;
  }

  .action-parameters {
    h4 {
      font-size: 1rem;
      font-weight: 600;
      margin-bottom: 0.75rem;
      color: var(--df-body-color);
    }
  }

  /* Topic editor styles */
  .selected-topic-panel {
    border: 2px solid var(--primary-color);
  }

  .topic-editor-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1rem;
  }

  .topic-editor-field {
    label {
      display: block;
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--df-body-color);
      margin-bottom: 0.25rem;
    }
  }

  /* Publish settings */
  .publish-settings-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1rem;
  }

  .publish-setting {
    label {
      display: block;
      font-weight: 500;
      font-size: 0.85rem;
      color: var(--text-color-secondary);
      margin-bottom: 0.25rem;
    }
  }

  .panel-horizontal-wrap {
    display: flex;
    flex-wrap: wrap;
    padding-left: 0;
    margin-bottom: 0;
    border-radius: 0.25rem;
    overflow: hidden;
    /* This one is important */
  }

  .empty-action {
    padding: 3px 15px;
    border-radius: 10px;
    position: relative;
    margin: 0 0 10px;
  }

  .action-layout {
    padding: 3px 15px;
    border-radius: 10px;
    position: relative;
    margin: 0 0 10px;
    width: fit-content;
  }

  .action-transition-layout {
    padding: 3px 15px;
    border-radius: 10px;
    position: relative;
    margin: 0 0 10px;
    width: 150px;
  }

  .tooltip-width {
    max-width: none !important;
  }

  .list-group-item.h-100.d-flex.justify-content-between.sortable-chosen.action-transition-layout {
    width: fit-content !important;
    border-radius: 0.25rem !important;
    margin: 0.5rem !important;
  }

  .action-transition-layout {
    filter: blur(1px);
    background: #c8ebfb !important;
    width: fit-content !important;

    .p-inputtext {
      background: #c8ebfb !important;
      border-radius: none !important;
      transition: unset !important;
    }
  }

  .inputtext-border-remove {
    border: none !important;
  }

  .inputtext-border-remove {
    &.p-inputtext::placeholder {
      color: red;
    }
  }

  .action-panel-content {
    position: relative;
    display: block;
    padding: none;
    background-color: var(--surface-card, #fff);
    border: 1px solid var(--df-border-color, rgba(0, 0, 0, 0.125));
    border-bottom-right-radius: 0.25rem !important;
    border-bottom-left-radius: 0.25rem !important;
  }
}

/* Action picker dialog */
.action-picker-content {
  min-height: 300px;
}

.action-picker-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.action-picker-table thead {
  position: sticky;
  top: 0;
  background: var(--surface-card);
  z-index: 1;
}

.action-picker-table th {
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid var(--surface-border);
  font-weight: 600;
  color: var(--text-color-secondary);
  font-size: 0.8rem;
  text-transform: uppercase;
}

.action-picker-table tbody {
  display: block;
  max-height: 400px;
  overflow-y: auto;
}

.action-picker-table thead,
.action-picker-table tbody tr {
  display: table;
  width: 100%;
  table-layout: fixed;
}

.action-picker-table .col-action {
  width: 26%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-picker-table .col-plugin {
  width: 24%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-picker-table .col-description {
  width: 50%;
}

.action-picker-row {
  cursor: pointer;
  transition: background-color 0.15s;
}

.action-picker-row td {
  padding: 0.65rem 1rem;
  border-bottom: 1px solid var(--surface-border);
  vertical-align: top;
}

.action-picker-row:hover {
  background-color: var(--surface-hover);
}

.action-picker-row.selected {
  background-color: var(--df-palette-selected-bg);
}

.action-picker-row.selected td:first-child {
  border-left: 3px solid var(--primary-color);
  padding-left: calc(1rem - 3px);
}

.action-picker-row .col-action {
  font-weight: 600;
  color: var(--text-color);
}

.action-picker-row .col-plugin {
  color: var(--text-color-secondary);
}

.action-picker-row .col-description {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
}

.action-description-preview {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.action-description-preview.expanded {
  gap: 0.5rem;
}

.action-description-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  white-space: normal;
  word-break: break-word;
}

.action-description-more {
  color: var(--primary-color);
  font-size: 0.8rem;
  font-weight: 600;
}

.action-description-expanded {
  color: var(--text-color-secondary);
  line-height: 1.45;
  white-space: normal;
}

.action-description-expanded :deep(p),
.action-description-expanded :deep(ul),
.action-description-expanded :deep(ol),
.action-description-expanded :deep(pre),
.action-description-expanded :deep(blockquote) {
  margin: 0 0 0.75rem;
}

.action-description-expanded :deep(*:last-child) {
  margin-bottom: 0;
}

.action-description-expanded :deep(code) {
  padding: 0.1rem 0.3rem;
  border-radius: 0.25rem;
  background: var(--df-subtle-border);
  font-size: 0.85em;
}

.action-description-expanded :deep(pre) {
  padding: 0.75rem;
  border-radius: 0.375rem;
  background: var(--df-subtle-bg-alt);
  overflow-x: auto;
}

.action-picker-row .no-description {
  color: var(--surface-400);
}

.action-picker-empty {
  padding: 2rem;
  text-align: center;
  color: var(--text-color-secondary);
}

/* Transform panel header */
.transform-panel-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.transform-name {
  font-weight: 600;
  font-size: 1.1rem;
}

.transform-description {
  color: var(--text-color-secondary);
  font-size: 0.9rem;
}

/* Editor dialog content */
.action-editor-content,
.topic-editor-content,
.publish-settings-content {
  .field {
    margin-bottom: 1rem;

    label {
      display: block;
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text-color);
      margin-bottom: 0.5rem;
    }

    .w-full {
      width: 100%;
    }
  }
}

.topic-editor-content .p-dropdown {
  width: 100% !important;
}

.transform-raw-json-dialog {
  pre {
    padding: 3px;
    margin: 3px;
  }

  .string {
    color: #e91e63;
  }

  .number {
    color: #00bcd4;
  }

  .boolean {
    color: #ff00ff;
  }

  .null {
    color: #9e9e9e;
  }

  .key {
    color: #3753a9;
  }
}

.help-button {
  padding: 1px 4px !important;
}

.help-dialog {
  h2 {
    margin: 2rem 0 1rem 0 !important;
    font-size: 2rem;
    font-weight: 300;
    line-height: 1.2;
    border-bottom: 1px solid var(--df-border-color);
    padding-bottom: 5px;
  }

  h3 {
    margin: 2rem 0 1rem 0 !important;
    font-size: 1.5rem !important;
    font-weight: 250;
    line-height: 1.1;
  }

  table {
    margin-bottom: 20px;
  }

  table,
  th,
  td {
    border-collapse: collapse;
    border: 1px solid var(--df-border-color);
  }

  th,
  td {
    padding: 10px;
  }

  th {
    background-color: #eee;
  }
}
</style>
