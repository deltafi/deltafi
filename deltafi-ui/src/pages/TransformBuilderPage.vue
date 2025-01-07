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
  <div ref="transformBuilderPage" class="transform-builder-page">
    <PageHeader>
      <template #header>
        <div class="align-items-center btn-group">
          <h2 class="mb-0">{{ transformBuilderHeader }}</h2>
          <div v-if="model.active" class="btn-group">
            <DialogTemplate component-name="transformBuilder/TransformConfigurationDialog" :header="`Edit ${model.name}`" dialog-width="25vw" model-position="center" :data-prop="model" :edit-transform="editExistingTransform" @create-transform="setTransformValues">
              <Button v-tooltip.top="`Edit Description`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary ml-2" />
            </DialogTemplate>
          </div>
        </div>
      </template>
    </PageHeader>
    <div v-if="model.active">
      <div class="row p-2">
        <div class="col pl-2 pr-1">
          <Panel header="Subscribe" :pt="{ content: { class: 'p-1' } }">
            <template #icons>
              <Badge v-if="!_.isEmpty(validateSubscribe)" v-tooltip.left="{ value: `${validateSubscribe}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
            </template>
            <div class="px-0">
              <dd>
                <div class="deltafi-fieldset">
                  <div class="px-2">
                    <JsonForms :data="model['subscribe']" :renderers="subscribeRenderers" :uischema="subscribeUISchema" :schema="subscribeSchema" :config="formsConfig" :ajv="handleDefaultsAjv" @change="onSubscribeChange" />
                  </div>
                </div>
              </dd>
            </div>
          </Panel>
        </div>
      </div>
      <div class="row p-2">
        <div class="col pl-2 pr-1">
          <div>
            <Panel header="Transform Actions" class="table-panel">
              <template #icons>
                <button class="p-panel-header-icon p-link" @click="viewTransformActionsPicker($event)">
                  <span class="pi pi-plus-circle"></span>
                </button>
              </template>
              <div class="action-panel-content p-2">
                <template v-if="transformActions.length == 0">
                  <div class="empty-action pt-2 mb-n3">No Transform Actions</div>
                </template>
                <draggable id="transformActions" v-model="transformActions" item-key="id" :sort="true" group="transformActions" ghost-class="action-transition-layout" drag-class="action-transition-layout" class="dragArea panel-horizontal-wrap pb-2 pt-3" @change="validateNewAction" @move="actionOrderChanged">
                  <template #item="{ element, index }">
                    <div :id="element.id" class="action-layout border border-dark rounded mx-2 my-4 p-overlay-badge">
                      <Badge v-if="!_.isEmpty(validateAction(element))" v-tooltip.left="{ value: `${validateAction(element)}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
                      <div class="d-flex align-items-center justify-content-between">
                        <span class="one-line">
                          <InputText v-model="element.name" :class="'inputtext-border-remove pl-0 text-truncate'" placeholder="Action Name Required" />
                        </span>
                        <div class="pl-2 btn-group">
                          <DialogTemplate component-name="transformBuilder/ActionConfigurationDialog" :header="`Edit ${displayActionName(element)}`" :row-data-prop="element" :action-index-prop="index" dialog-width="75vw" @update-action="updateAction">
                            <Button v-tooltip.top="{ value: `Edit ${displayActionName(element)}`, class: 'tooltip-width', showDelay: 300 }" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
                          </DialogTemplate>
                          <Button v-tooltip.top="{ value: `Remove ${displayActionName(element)}`, class: 'tooltip-width', showDelay: 300 }" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="removeAction(index)" />
                        </div>
                      </div>
                      <Divider class="my-0" />
                      <dd>
                        <span v-tooltip.bottom="{ value: `${element.type}`, class: 'tooltip-width', showDelay: 300 }">{{ element.displayName }}</span>
                      </dd>
                    </div>
                  </template>
                </draggable>
              </div>
            </Panel>
          </div>
        </div>
      </div>
      <div class="row p-2">
        <div class="col pl-2 pr-1">
          <Panel header="Publish" :pt="{ content: { class: 'p-1' } }">
            <template #icons>
              <Badge v-if="!_.isEmpty(validatePublish)" v-tooltip.left="{ value: `${validatePublish}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
            </template>
            <div class="px-0">
              <dd>
                <div class="deltafi-fieldset">
                  <div class="px-2">
                    <JsonForms ref="schemaForm" :data="model['publish']" :renderers="publishRenderers" :uischema="publishUISchema" :schema="publishSchema" :ajv="handleDefaultsAjv" :config="formsConfig" @change="onPublishChange" />
                  </div>
                </div>
              </dd>
            </div>
          </Panel>
        </div>
      </div>
    </div>
    <HoverSaveButton v-if="model.active" target="window" :model="items" />
    <Dialog ref="actionsOverlayPanel" v-model:visible="actionPickerVisible" header="Available Actions" class="transform-builder-page-overlay" :dismissable-mask="false" style="width: 50%" position="right">
      <Splitter style="height: 80vh" layout="vertical" :gutter-size="10" @resizeend="customSpitterSize">
        <SplitterPanel id="splitterPanelId" :size="startingPanelOneSize" class="flex align-items-center justify-content-center" :style="`overflow-y: auto; ${panelOneSize}`">
          <DataTable id="dataTableId" ref="dataTableIdRef" v-model:selection="selectedTransformAction" v-model:filters="filters" :value="actionsDataTable" selection-mode="single" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines plugin-table" sort-field="displayName" :sort-order="1" :row-hover="true" :meta-key-selection="false" :global-filter-fields="['displayName', 'pluginCoordinate.groupId', 'pluginCoordinate.version']" data-key="id">
            <template #header>
              <div class="flex justify-content-end">
                <span class="p-input-icon-right">
                  <i class="pi pi-search" />
                  <InputText v-model="filters['global'].value" placeholder="Keyword Search" />
                </span>
              </div>
            </template>
            <template #empty>No Actions found.</template>
            <template #loading>Loading Actions. Please wait.</template>
            <Column header-style="width: 3em">
              <template #body="{ data }">
                <draggable ref="draggableRef" class="dragArea list-group align-items-center justify-content-center w-100 h-100" :list="[data]" :group="{ name: 'transformActions', pull: 'clone', put: false }" item-key="displayName" :clone="cloneAction" ghost-class="tree-action" drag-class="tree-action">
                  <template #item="{ element }">
                    <span class="d-flex align-items-center justify-content-center w-100 h-100">
                      <i v-tooltip.bottom="`Grabbing ${element.displayName}`" :class="['p-datatable-reorderablerow-handle', 'pi pi-bars', 'd-flex', 'align-items-center', 'justify-content-center', 'w-100', 'h-100']"></i>
                    </span>
                  </template>
                </draggable>
              </template>
            </Column>
            <Column field="displayName" header="Name"></Column>
            <Column field="pluginCoordinate.groupId" header="Group Id" :sortable="true" class="truncate-column">
              <template #body="{ data }">
                <div v-if="data.pluginCoordinate.groupId > 16" v-tooltip.top="data.pluginCoordinate.groupId" class="truncate">{{ data.pluginCoordinate.groupId }}</div>
                <div v-else>{{ data.pluginCoordinate.groupId }}</div>
              </template>
            </Column>
            <Column field="pluginCoordinate.version" header="Version" :sortable="true" class="truncate-column">
              <template #body="{ data }">
                <div v-if="data.pluginCoordinate.version > 16" v-tooltip.top="data.pluginCoordinate.version" class="truncate">{{ data.pluginCoordinate.version }}</div>
                <div v-else>{{ data.pluginCoordinate.version }}</div>
              </template>
            </Column>
          </DataTable>
        </SplitterPanel>
        <SplitterPanel :size="startingPanelTwoSize" :style="`overflow-y: auto; ${panelTwoSize}`">
          <div v-if="helpVisible" class="help-dialog">
            <div class="p-3" v-html="markdownIt.render(helpMarkdown)" />
          </div>
        </SplitterPanel>
      </Splitter>
    </Dialog>
    <DialogTemplate component-name="transformBuilder/TransformConfigurationDialog" header="Create New Transform" dialog-width="25vw" model-position="center" :closable="false" :disable-model="true" :data-prop="model" @create-transform="createFlowPlan">
      <span id="CreateFlowPlan" />
    </DialogTemplate>
    <LeavePageConfirmationDialog header="Leaving Transform Builder" message="There is a transform build in progress with unsaved changes. Leaving the page will erase those changes. Are you sure you want to leave this page?" :match-condition="transformInProgress()" />
    <Dialog v-model:visible="displayRawJsonDialog" :style="{ width: '90vw' }" modal maximizable close-on-escape dismissable-mask :draggable="false" header="Transform Raw Json" class="transform-raw-json-dialog" @hide="hideTransformRawJsonDialog">
      <Panel header="Output">
        <template #icons>
          <Button v-tooltip.left="'Show Schema'" class="p-panel-header-icon p-link p-me-2" @click="showSchema()">
            <span class="fa-solid fa-file-invoice" />
          </Button>
          <Button v-tooltip.left="'Copy to Clipboard'" class="p-panel-header-icon p-link p-me-2" @click="copy(rawOutput)">
            <span class="fa-solid fa-copy" />
          </Button>
        </template>
        <pre class="textAreaWidth" style="text-align: start; white-space: pre-wrap; overflow: auto; border-bottom: none" v-html="prettyPrint(rawOutput)"></pre>
      </Panel>
    </Dialog>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import HoverSaveButton from "@/components/transformBuilder/HoverSaveButton.vue";
import LeavePageConfirmationDialog from "@/components/LeavePageConfirmationDialog.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowActions from "@/composables/useFlowActions";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import useTopics from "@/composables/useTopics";
import { computed, nextTick, onBeforeMount, provide, ref, watch } from "vue";
import { FilterMatchMode } from "primevue/api";
import { StorageSerializers, useClipboard, useMagicKeys, useResizeObserver, useStorage } from "@vueuse/core";
import { useRouter } from "vue-router";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";
import { createAjv } from "@jsonforms/core";

import Badge from "primevue/badge";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Divider from "primevue/divider";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import Splitter from "primevue/splitter";
import SplitterPanel from "primevue/splitterpanel";

import draggable from "vuedraggable";

import { jsPlumb } from "jsplumb";
import $ from "jquery";
import _ from "lodash";

import MarkdownIt from "markdown-it";

const markdownIt = new MarkdownIt({
  html: true,
});
const helpMarkdown = ref("");
const helpVisible = ref(false);
const actionPickerVisible = ref(false);
const helpHeader = ref("Action Help");
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
const { myStyles, publishRenderList, subscribeRenderList } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const publishRenderers = ref(Object.freeze(publishRenderList));
const subscribeRenderers = ref(Object.freeze(subscribeRenderList));
const subscribeUISchema = ref(undefined);

const schemaForm = ref(null);
const draggableRef = ref(null);

const allActionsData = ref({});

const transformBuilderPage = ref(null);
const pageWidthResizeObserver = ref(null);
const editExistingTransform = ref(false);
const originalTransform = ref(null);

const schemaVisible = ref(false);
const displayRawJsonDialog = ref(false);

const allFlowPlanData = ref({});

const formsConfig = ref({ defaultLabels: true });

const selectedTransformAction = ref(null);
const startingPanelOneSize = ref(99);
const startingPanelTwoSize = ref(1);
const panelOneSize = ref(null);
const panelTwoSize = ref(null);
const userResized = ref(false);

watch(selectedTransformAction, async (newItem) => {
  if (newItem === null || newItem === undefined) {
    panelOneSize.value = !userResized.value ? splitterSize(99) : panelOneSize.value;
    panelTwoSize.value = !userResized.value ? splitterSize(1) : panelTwoSize.value;
  } else {
    showHelp(newItem);
    panelOneSize.value = !userResized.value ? splitterSize(50) : panelOneSize.value;
    panelTwoSize.value = !userResized.value ? splitterSize(50) : panelTwoSize.value;
  }
});

const splitterSize = (slitSize) => {
  return `flex-basis: calc(${slitSize}% - 10px);`;
};

const customSpitterSize = async (event) => {
  userResized.value = true;
  await nextTick();
  panelOneSize.value = splitterSize(event.sizes[0]);
  panelTwoSize.value = splitterSize(event.sizes[1]);
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

// The useResizeObserver determines if the sidebar has been collapsed or expanded.
// If either has occurred we redo the connections between all actions.
useResizeObserver(transformBuilderPage, (entries) => {
  const [entry] = entries;
  const { width } = entry.contentRect;
  if (!pageWidthResizeObserver.value) {
    pageWidthResizeObserver.value = width;
  } else {
    if (!_.isEqual(pageWidthResizeObserver.value, width)) {
      if (model.value.type) {
        connectActions();
      }
    }
  }
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

// The viewTransformActionsPicker function is triggered by clicking on the add button on the Transform panel.
const viewTransformActionsPicker = () => {
  actionsDataTable.value = flattenedActions.value;
  actionPickerVisible.value = true;
};

const showHelp = (action) => {
  helpMarkdown.value = action.docsMarkdown || "# No Docs Available";
  helpHeader.value = `${action.displayName} Action Help`;
  helpVisible.value = true;
};

const defaultTopicTemplate = [{ condition: null, topic: null }];

const transformTemplate = {
  type: "TRANSFORM",
  active: false,
  name: null,
  description: null,
  subscribe: defaultTopicTemplate,
  publish: {
    rules: defaultTopicTemplate,
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

onBeforeMount(async () => {
  let topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  getLoadedActions();

  let response = await getAllFlows();
  allFlowPlanData.value = response.data.getAllFlows;

  if (!_.isEmpty(_.get(linkedTransform.value, "transformParams", null))) {
    if (linkedTransform.value.transformParams.editExistingTransform) {
      editExistingTransform.value = true;
      let transformInfo = {};
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

const actionsDataTable = ref([]);
const transformJsPlumbInstance = ref({});

const transformBuilderHeader = computed(() => {
  let header = model.value.name ? `Transform Builder - ${model.value.name}` : "Transform Builder";
  return header;
});

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

  let getClonedActionsByTypes = _.cloneDeep(_.get(cloneTransform.selectedTransform, "transformActions"));
  if (!_.isEmpty(getClonedActionsByTypes)) {
    clonedTransformActions = clonedTransformActions.concat(getClonedActionsByTypes);
    if (!_.isEmpty(clonedTransformActions)) {
      for (let clonedAction of clonedTransformActions) {
        let tmpMergedActionAndActionSchema = _.cloneDeep(_.find(flattenedActions.value, { type: clonedAction.type, flowActionType: "TRANSFORM" }));
        let mergedActionAndActionSchema = _.merge(tmpMergedActionAndActionSchema, clonedAction);
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
  transformJsPlumbInstance.value = {};
};

const connectActions = async () => {
  await nextTick();

  // An instance of jsPlumb and stored if one has not already been created.
  // This allows for resetting of that specific jsPlumb instance every time we
  // need to redraw the connection.
  if (_.isEmpty(transformJsPlumbInstance.value)) {
    transformJsPlumbInstance.value = jsPlumb.getInstance();
  }

  let plumbIns = transformJsPlumbInstance.value;

  plumbIns.ready(function () {
    // Reset the action connects so we can redraw them.
    plumbIns.deleteEveryConnection();
    plumbIns.deleteEveryEndpoint();
    plumbIns.reset();
  });

  // Get all the actions in the panel.
  var actionsInPanel = [];
  $(`#transformActions`)
    .find("div")
    .each(function () {
      actionsInPanel.push(this);
    });
  // Remove all the actions that don't have an id.
  _.remove(actionsInPanel, { id: "" });

  // Connect all the actions in the panel
  for (let i = 0; i < actionsInPanel.length - 1; i++) {
    const current = actionsInPanel[i];
    const next = actionsInPanel[i + 1];

    let anchorType = [];
    // If the offsetTop of both the current and next are the same that the action hasn't wrapped to a new
    // line. Make the arrow come from the left of the first action to the right of the next action. If it has
    // wrapped make the arrow come from the bottom of the first action to the top of the next action.
    if (_.isEqual(current.offsetTop, next.offsetTop)) {
      anchorType = ["Left", "Right"];
      //anchorType = _.merge(anchorType, ["Left", "Right", "Top", "Bottom", [0.3, 0, 0, -1], [0.7, 0, 0, -1], [0.3, 1, 0, 1], [0.7, 1, 0, 1]]);
    } else {
      anchorType = ["Bottom", "Top"];
    }
    plumbIns.ready(function () {
      let defaultConnectionValues = {
        connector: ["Flowchart", { stub: 5 }],
        endpoint: "Blank",
        overlays: [["Arrow", { width: 8, length: 8, location: 1 }]], // overlay
        paintStyle: { stroke: "#909399", strokeWidth: 2 }, // connector
      };
      plumbIns.connect({
        id: `${current.id}-${next.id}`,
        source: `${current.id}`,
        target: `${next.id}`,
        deleteEndpointsOnDetach: true,
        ...defaultConnectionValues,
        anchor: anchorType,
        scope: "transformActions",
      });

      plumbIns.repaintEverything();
    });
  }
};

const addAction = async (action) => {
  let addNewAction = JSON.parse(JSON.stringify(action));
  addNewAction["id"] = _.uniqueId("transformActions");

  transformActions.value.push(addNewAction);
  connectActions();
};

const transformActionsChanged = () => {
  // Get all the keys from both original and rawOutput
  let allKeys = _.union(_.keys(originalTransform.value), _.keys(rawOutput.value));

  // Detect changes and deletions
  let changedTransformValues = _.omitBy(_.pick(rawOutput.value, allKeys), function (v, k) {
    return JSON.stringify(originalTransform.value[k]) === JSON.stringify(v);
  });

  // Detect deletions (keys present in originalTransform but missing in rawOutput)
  let deletedKeys = _.difference(_.keys(originalTransform.value), _.keys(rawOutput.value));

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
  let allTransformMissingFields = [];
  for (let action of _.flatten(transformActions.value)) {
    let actionMissingRequiredFields = validateAction(action);
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

const items = ref([
  {
    label: "Save",
    icon: "fa-solid fa-hard-drive",
    isEnabled: isValidTransform,
    visible: true,
    command: () => {
      save(rawOutput.value);
    },
  },
]);

const validateSubscribe = computed(() => {
  // If the subscribe field is empty return "Not Subscribing to any Topic."
  if (_.isEmpty(model.value["subscribe"])) {
    return "Not Subscribing to any Topic.";
  }

  let checkIfSubscribeHasTopic = (key) =>
    model.value["subscribe"].some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  var isKeyPresent = checkIfSubscribeHasTopic("topic");
  // If the subscribe field isn't empty but there isn't a topic return "Not Subscribing to any Topic Name."
  if (!isKeyPresent) {
    return "Not Subscribing to any Topic Name.";
  }

  return null;
});

const validatePublish = computed(() => {
  if (_.isEqual(model.value["publish"]?.defaultRule?.defaultBehavior, "PUBLISH") && _.isEmpty(model.value["publish"]?.defaultRule?.topic)) {
    return "Default Behavior of Publish requires a Topic.";
  }

  // If the Publish Rules field is empty return "Missing publish rules."
  if (_.isEmpty(model.value["publish"].rules)) {
    return "Missing publish rules.";
  }

  let checkIfPublishRulesHasTopic = (key) =>
    model.value["publish"].rules.some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  // If the Publish Rules field isn't empty but there isn't a topic return "Not Publishing to any Topic Name."
  var isKeyPresent = checkIfPublishRulesHasTopic("topic");

  if (!isKeyPresent) {
    return "Not Publishing to any Topic Name.";
  }

  return null;
});

const validateAction = (action) => {
  // List of all missing Fields in the action
  let missingFieldsInAction = [];
  // requiredSchemaFields is a list of all list of all the required fields for the action.
  let requiredSchemaFields = _.get(action.schema, "required", []);
  // completedFields is a list of all the keys of the fields that the user has filled in for the action.
  let completedFields = _.keys(_.get(action, "parameters", {}));

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
    let duplicateActionNamesInTransform = _.filter(_.flatten(transformActions.value), { name: action.name });
    if (duplicateActionNamesInTransform.length > 1) {
      duplicateActionNames = `All action names within a Transform have to be unique. Duplicate action name: ${action.name}.`;
    }
  }

  if (_.isEmpty(missingFieldsInAction) && _.isEmpty(duplicateActionNames)) {
    return null;
  }

  let invalidActionFields = `${_.isEmpty(duplicateActionNames) ? "" : duplicateActionNames} ${!_.isEmpty(duplicateActionNames) && !_.isEmpty(missingFieldsInAction) ? " and " : ""} ${_.isEmpty(missingFieldsInAction) ? "" : `missing required fields: ${missingFieldsInAction.join(", ")}`}`;

  return _.capitalize(invalidActionFields.trim());
};

const displayActionName = (action) => {
  if (_.isEmpty(action.name)) {
    return action.displayName;
  }

  return action.name;
};

const updateAction = (newActionValue) => {
  transformActions.value.splice(newActionValue["actionIndex"], 1, newActionValue["updatedAction"]);
  connectActions();
};

const removeAction = (index) => {
  transformActions.value.splice(index, 1);
  connectActions();
};

const cloneAction = (clonedAction) => {
  let addNewClonedAction = JSON.parse(JSON.stringify(clonedAction));
  addNewClonedAction["id"] = _.uniqueId(addNewClonedAction["flowActionType"]);
  return JSON.parse(JSON.stringify(addNewClonedAction));
};

const validateNewAction = async () => {
  connectActions();
};

// When there are multiple actions of a certain Action Type the order of the actions can be changed.
// If this happens we need to update the connections between each action. This
const actionOrderChanged = () => {
  connectActions();
};

const flattenedActions = ref([]);

const getLoadedActions = () => {
  for (const plugin of allActionsData.value) {
    for (const action of plugin["actions"]) {
      if (action.type === "TIMED_INGRESS") continue;

      // Reformatting each action.
      action["disableEdit"] = true;
      action["flowActionType"] = action["type"];
      action["type"] = action["name"];
      let displayName = action.name.split(".").pop();
      action["displayName"] = displayName;
      action["id"] = _.uniqueId(`${displayName}_`);
      let coordinateGrouping = action.name.split(".").slice(0, -1).join(".");
      action["coordinateGrouping"] = coordinateGrouping;
      action["pluginCoordinate"] = plugin.pluginCoordinates;
      action["name"] = "";

      action["parameters"] = {};
      if (!_.isEmpty(action.schema.properties)) {
        for (const [key, value] of Object.entries(action.schema.properties)) {
          if (!_.isEmpty(value.default) || _.isBoolean(value.default) || _.isNumber(value.default)) {
            action["parameters"][key] = value.default;
          }
        }
      }

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
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
    } else {
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ ...attrs }) => _.pick(attrs, Object.keys([transformActionsTemplate][0])));
      displayOutput["transformActions"] = displayOutput["transformActions"].map(({ schema, description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
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

    let test = stringifiedReplaced.replace(regex, (match) => {
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

<style lang="scss">
@import "@/styles/pages/transform-builder-page.scss";
</style>
