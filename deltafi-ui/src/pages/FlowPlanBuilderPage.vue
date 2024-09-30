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
  <div ref="flowPlanBuilderPage" class="flow-plan-builder-page">
    <PageHeader>
      <template #header>
        <div class="align-items-center btn-group">
          <h2 class="mb-0">{{ flowPlanHeader }}</h2>
          <div v-if="model.active" class="btn-group">
            <DialogTemplate component-name="flowBuilder/FlowConfigurationDialog" :header="`Edit ${model.name}`" dialog-width="25vw" model-position="center" :data-prop="model" :edit-flow-plan="editExistingFlowPlan" @create-flow-plan="setFlowValues">
              <Button v-tooltip.top="`Edit Name and Description`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary ml-2" />
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
                    <JsonForms :data="model['subscribe']" :renderers="renderers" :uischema="subscribeUISchema" :schema="subscribeSchema" :config="formsConfig" @change="onSubscribeChange" />
                  </div>
                </div>
              </dd>
            </div>
          </Panel>
        </div>
      </div>
      <div class="row p-2">
        <div v-for="action of flowTypesMap.get(model.type).flowActionTypes" :key="action" class="col pl-2 pr-1">
          <div>
            <Panel :header="_.startCase(flowActionTemplateMap.get(action).activeContainer)" class="table-panel">
              <template #icons>
                <button class="p-panel-header-icon p-link" @click="viewActionTreeMenu($event, action)">
                  <span class="pi pi-plus-circle"></span>
                </button>
              </template>
              <div class="action-panel-content p-2">
                <template v-if="flowActionTemplateObject[flowActionTemplateMap.get(action).activeContainer].length == 0">
                  <div class="empty-action pt-2 mb-n3">No {{ _.startCase(flowActionTemplateMap.get(action).activeContainer) }}</div>
                </template>
                <draggable :id="action" v-model="flowActionTemplateObject[flowActionTemplateMap.get(action).activeContainer]" item-key="id" :sort="true" :group="action" ghost-class="action-transition-layout" drag-class="action-transition-layout" class="dragArea panel-horizontal-wrap pb-2 pt-3" @change="validateNewAction" @move="actionOrderChanged">
                  <template #item="{ element, index }">
                    <div :id="element.id" class="action-layout border border-dark rounded mx-2 my-4 p-overlay-badge">
                      <Badge v-if="!_.isEmpty(validateAction(element))" v-tooltip.left="{ value: `${validateAction(element)}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
                      <div class="d-flex align-items-center justify-content-between">
                        <span class="one-line">
                          <InputText v-model="element.name" :class="'inputtext-border-remove pl-0 text-truncate'" placeholder="Action Name Required" />
                        </span>
                        <div class="pl-2 btn-group">
                          <DialogTemplate component-name="flowBuilder/ActionConfigurationDialog" :header="`Edit ${displayActionName(element)}`" :row-data-prop="element" :action-index-prop="index" dialog-width="75vw" @update-action="updateAction">
                            <Button v-tooltip.top="{ value: `Edit ${displayActionName(element)}`, class: 'tooltip-width', showDelay: 300 }" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
                          </DialogTemplate>
                          <Button v-tooltip.top="{ value: `Remove ${displayActionName(element)}`, class: 'tooltip-width', showDelay: 300 }" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="removeAction(element, index)" />
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
                    <JsonForms ref="schemaForm" :data="model['publish']" :renderers="renderers" :uischema="publishUISchema" :schema="publishSchema" :ajv="handleDefaultsAjv" :config="formsConfig" @change="onPublishChange" />
                  </div>
                </div>
              </dd>
            </div>
          </Panel>
        </div>
      </div>
    </div>
    <HoverSaveButton v-if="model.active" target="window" :model="items" />
    <OverlayPanel ref="actionsOverlayPanel" class="flow-plan-builder-page-overlay" append-to="body" dismissable show-close-icon style="width: 25%">
      <Tree
        ref="actionsTreeRef"
        v-model:expandedKeys="expandedKeys"
        :value="actionsTree"
        :filter="true"
        filter-mode="strict"
        filter-by="filterField"
        :pt="{
          container: { class: 'tree-panel-content-height' },
        }"
      >
        <template #default="slotProps">
          <b>{{ slotProps.node.label }}</b>
        </template>
        <template #actions="slotProps">
          <draggable v-model="slotProps.node.data" item-key="name" :sort="false" :group="{ name: flowActionTypeGroup, pull: 'clone', put: false }" :clone="cloneAction" ghost-class="tree-action" drag-class="tree-action" class="list-group mb-0">
            <template #item="{ element }">
              <div class="list-group-item h-100 d-flex action-item-width justify-content-between">
                <div class="btn-group">
                  <i :class="actionTemplateClass(element)" @click="addAction(flowActionTypeGroup, element)"></i>
                  <div>{{ element.displayName }}</div>
                </div>
                <div v-if="element.coordinateGrouping.startsWith('org.deltafi.core.action')">
                  <a :href="'/docs/#/core-actions/' + element.coordinateGrouping + '.' + element.displayName" target="_blank" class="align-middle">
                    <i class="pi pi-question-circle text-muted" />
                  </a>
                </div>
              </div>
            </template>
          </draggable>
        </template>
      </Tree>
    </OverlayPanel>
    <DialogTemplate component-name="flowBuilder/FlowConfigurationDialog" header="Create New Flow Plan" dialog-width="25vw" model-position="center" :closable="false" :disable-model="true" :data-prop="model" @create-flow-plan="createFlowPlan">
      <span id="CreateFlowPlan" />
    </DialogTemplate>
    <LeavePageConfirmationDialog header="Leaving Flow Plan Builder" message="There is a flow plan in progress with unsaved changes. Leaving the page will erase those changes. Are you sure you want to leave this page?" :match-condition="flowPlanInProgress()" />
    <Dialog v-model:visible="displayRawJsonDialog" :style="{ width: '90vw' }" modal maximizable close-on-escape dismissable-mask :draggable="false" header="Flows Plan Raw Json" class="flow-plan-raw-json-dialog" @hide="flowPlanRawJsonDialogHide">
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
import HoverSaveButton from "@/components/flowBuilder/HoverSaveButton.vue";
import LeavePageConfirmationDialog from "@/components/LeavePageConfirmationDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowActions from "@/composables/useFlowActions";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, nextTick, onBeforeMount, provide, ref, watch } from "vue";
import { StorageSerializers, useClipboard, useMagicKeys, useResizeObserver, useStorage } from "@vueuse/core";
import { useRouter } from "vue-router";
import useTopics from "@/composables/useTopics";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";
import { createAjv } from "@jsonforms/core";

const handleDefaultsAjv = createAjv({ useDefaults: true });

import Badge from "primevue/badge";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Divider from "primevue/divider";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import OverlayPanel from "primevue/overlaypanel";
import Tree from "primevue/tree";
import draggable from "vuedraggable";

import { jsPlumb } from "jsplumb";
import $ from "jquery";
import _ from "lodash";

const { getAllTopics } = useTopics();
const { getAllFlows } = useFlowQueryBuilder();
const { getPluginActionSchema } = useFlowActions();
const { saveTransformFlowPlan } = useFlowPlanQueryBuilder();
const keys = useMagicKeys();
const devKey = keys["d+e+v"];
const { copy } = useClipboard();
const notify = useNotifications();
const router = useRouter();
const actionsOverlayPanel = ref();
const actionsTreeRef = ref(null);
const allTopics = ref(["default"]);
const { myStyles, rendererList } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const subscribeUISchema = ref(undefined);

const schemaForm = ref(null);

const allActionsData = ref({});

const expandedKeys = ref({});

const flowPlanBuilderPage = ref(null);
const pageWidthResizeObserver = ref(null);
const editExistingFlowPlan = ref(false);
const originalFlowPlan = ref(null);

const schemaVisible = ref(false);
const displayRawJsonDialog = ref(false);

const allFlowPlanData = ref({});

const formsConfig = ref({ defaultLabels: true });

// The useResizeObserver determines if the sidebar has been collapsed or expanded.
// If either has occurred we redo the connections between all actions.
useResizeObserver(flowPlanBuilderPage, (entries) => {
  const [entry] = entries;
  const { width } = entry.contentRect;
  if (!pageWidthResizeObserver.value) {
    pageWidthResizeObserver.value = width;
  } else {
    if (!_.isEqual(pageWidthResizeObserver.value, width)) {
      if (model.value.type) {
        for (let flowActionType of flowTypesMap.get(model.value.type).flowActionTypes) {
          connectActions(flowActionType);
        }
      }
    }
  }
  pageWidthResizeObserver.value = width;
});

// This watch on key pressed state on "d+e+v" will activate the dialog to view the raw JSON of a flow plan.
watch(devKey, (v) => {
  if (v) {
    if (model.value.active) {
      displayRawJsonDialog.value = !displayRawJsonDialog.value;
    }
  }
});

// This watch on filterValue expands the tree to the full depth for values
// that match the search filter in the action tree.
watch(
  () => actionsTreeRef.value?.filterValue,
  (data) => {
    // The tree components filteredValue is a very temperamental computed value.
    // Making sure its not empty then making sure its not a string with just spaces
    if (!_.isEmpty(data)) {
      if (!_.isEmpty(data.trim())) {
        let expandedTreeObject = {};
        if (!_.isEmpty(actionsTreeRef.value.filteredValue)) {
          for (let element of actionsTreeRef.value.filteredValue) {
            expandedTreeObject[element.label] = true;
          }
        }
        expandedKeys.value = expandedTreeObject;
      } else {
        expandedKeys.value = {};
      }
    } else {
      expandedKeys.value = {};
    }
  }
);

const linkedFlowPlan = useStorage("linked-flow-plan-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

// The viewActionTreeMenu function is triggered by clicking on the add button on each flowActionType panel.
const viewActionTreeMenu = (event, flowActionType) => {
  // The flowActionTypeGroup is the value used to dynamically set the group variable in the draggable component
  // for linking the draggable actions to their respective flowActionType.
  flowActionTypeGroup.value = flowActionType;
  // The actionsTree is the value used to dynamically provide the array of actions for each flowActionType.
  actionsTree.value = actionTypesTree.value[flowActionType];
  actionsOverlayPanel.value.toggle(event);
};

const flowTemplate = {
  type: "TRANSFORM",
  active: false,
  name: null,
  description: null,
  subscribe: [],
  publish: {},
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

const flowPlan = ref(JSON.parse(JSON.stringify(flowTemplate)));

const flowTypesMap = new Map([
  [
    "TRANSFORM",
    {
      flowActionTypes: ["TRANSFORM"],
      activeContainerList: function () {
        return this.flowActionTypes.flatMap((v) => [flowActionTemplateMap.get(v).activeContainer]);
      },
    },
  ],
]);

const flowActionTemplateMap = new Map([["TRANSFORM", { selectTemplate: [transformActionsTemplate], activeContainer: "transformActions", limit: false, requiredActionMin: false }]]);

const flowActionTemplateObject = ref({
  transformActions: [],
});

const originalFlowActionTemplateObject = JSON.parse(JSON.stringify(flowActionTemplateObject.value));

onBeforeMount(async () => {
  let topics = await getAllTopics();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  getLoadedActions();

  let response = await getAllFlows();
  allFlowPlanData.value = response.data.getAllFlows;

  if (!_.isEmpty(_.get(linkedFlowPlan.value, "flowPlanParams", null))) {
    if (linkedFlowPlan.value.flowPlanParams.editExistingFlow) {
      editExistingFlowPlan.value = true;
      let flowInfo = {};
      flowInfo["type"] = _.toUpper(linkedFlowPlan.value.flowPlanParams.type);
      flowInfo["name"] = linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName;
      flowInfo["selectedFlowPlan"] = _.find(allFlowPlanData.value[`${_.toLower(linkedFlowPlan.value.flowPlanParams.type)}`], { name: linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName });
      flowInfo["description"] = flowInfo["selectedFlowPlan"].description;
      if (_.has(linkedFlowPlan.value.flowPlanParams.selectedFlowPlan, "subscribe")) {
        flowInfo["subscribe"] = linkedFlowPlan.value.flowPlanParams.selectedFlowPlan.subscribe || [];
      }
      if (_.has(linkedFlowPlan.value.flowPlanParams.selectedFlowPlan, "publish")) {
        flowInfo["publish"] = linkedFlowPlan.value.flowPlanParams.selectedFlowPlan.publish || {};
      }
      await createFlowPlan(flowInfo);
      originalFlowPlan.value = rawOutput.value;
    } else {
      model.value.type = _.toUpper(linkedFlowPlan.value.flowPlanParams.type);
      model.value.selectedFlowPlan = _.find(allFlowPlanData.value[`${_.toLower(linkedFlowPlan.value.flowPlanParams.type)}`], { name: linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName });
      document.getElementById("CreateFlowPlan").click();
    }
    linkedFlowPlan.value = null;
  } else {
    document.getElementById("CreateFlowPlan").click();
  }
});

const model = computed({
  get() {
    return new Proxy(flowPlan.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      flowPlan.value,
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

const flowActionTypeGroup = ref("");
const actionsTree = ref([]);
const flowActionSpecificJsPlumbInstance = ref({});

const flowPlanHeader = computed(() => {
  let header = model.value.name ? `Flow Plan Builder - ${model.value.name}` : "Flow Plan Builder";
  return header;
});

const createFlowPlan = async (newFlowPlan) => {
  removeFlow();
  await setFlowValues(newFlowPlan);
  if (newFlowPlan.selectedFlowPlan) {
    cloneFlow(newFlowPlan);
  }
};

const setFlowValues = async (flowInfo) => {
  await nextTick();
  model.value.type = flowInfo["type"];
  model.value.name = flowInfo["name"];
  model.value.description = flowInfo["description"];
  model.value.selectedFlowPlan = flowInfo["selectedFlowPlan"];

  if (_.has(flowInfo["selectedFlowPlan"], "subscribe")) {
    model.value["subscribe"] = flowInfo["selectedFlowPlan"].subscribe || [];
  }

  if (_.has(flowInfo["selectedFlowPlan"], "publish")) {
    model.value["publish"] = flowInfo["selectedFlowPlan"].publish || {};
  }
  model.value.active = true;
};

const cloneFlow = async (cloneFlow) => {
  for (let flowActionType of flowTypesMap.get(model.value.type).flowActionTypes) {
    let clonedActionsByTypes = [];
    let getClonedActionsByTypes = _.get(cloneFlow.selectedFlowPlan, flowActionTemplateMap.get(flowActionType).activeContainer);
    if (_.isEmpty(getClonedActionsByTypes)) {
      getClonedActionsByTypes = [];
      continue;
    }

    clonedActionsByTypes = clonedActionsByTypes.concat(getClonedActionsByTypes);
    if (!_.isEmpty(clonedActionsByTypes)) {
      for (let clonedAction of clonedActionsByTypes) {
        let tmpMergedActionAndActionSchema = _.find(flattenedActionsTypes.value[flowActionType], { type: clonedAction.type, flowActionType: flowActionType });
        let mergedActionAndActionSchema = _.merge(tmpMergedActionAndActionSchema, clonedAction);
        addAction(flowActionType, mergedActionAndActionSchema);
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

const save = async (rawFlow) => {
  let response = null;
  let newRawFlow = JSON.parse(JSON.stringify(rawFlow));
  newRawFlow = clearEmptyObjects(newRawFlow);
  response = await saveTransformFlowPlan(newRawFlow);
  if (response !== undefined) {
    notify.success(`${response.data[`save${_.capitalize(model.value.type)}FlowPlan`].name} Flow Plan Saved`);
    model.value.active = false;
    // Null out the originalFlowPlan value as that flow plan has changed.
    originalFlowPlan.value = null;
    router.push({ path: `/config/flows` });
  }
};

const removeFlow = () => {
  model.value = JSON.parse(JSON.stringify(flowTemplate));
  flowActionTemplateObject.value = JSON.parse(JSON.stringify(originalFlowActionTemplateObject));
  flowActionSpecificJsPlumbInstance.value = {};
};

const connectActions = async (flowActionType) => {
  await nextTick();

  // An instance of jsPlumb is created for each flowActionType and stored in an object if one is not already
  // present for that flowActionType. This allows for resetting of that specific jsPlumb instance every time we
  // need to redraw the connection.
  if (!_.get(flowActionSpecificJsPlumbInstance.value, flowActionType, null)) {
    flowActionSpecificJsPlumbInstance.value[flowActionType] = jsPlumb.getInstance();
  }

  let plumbIns = flowActionSpecificJsPlumbInstance.value[flowActionType];

  plumbIns.ready(function () {
    // Reset the action connects so we can redraw them.
    plumbIns.deleteEveryConnection();
    plumbIns.deleteEveryEndpoint();
    plumbIns.reset();
  });

  // Get all the actions in the panel.
  var actionsInPanel = [];
  $(`#${flowActionType}`)
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
        scope: flowActionType,
      });

      plumbIns.repaintEverything();
    });
  }
};

const addAction = async (flowActionType, action) => {
  let addNewAction = JSON.parse(JSON.stringify(action));
  addNewAction["id"] = _.uniqueId(flowActionType);

  flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer].push(addNewAction);
  connectActions(flowActionType);
};

// Validates all parts of the Flow Plan. If invalid it disables the save button.
const isValidFlow = computed(() => {
  // If the Flow Plans Raw Dialog or the Schema is visible disable the save
  if (schemaVisible.value || displayRawJsonDialog.value) {
    return false;
  }

  // If we are editing an existing flow and we haven't made any changes disable the save
  if (!_.isEmpty(originalFlowPlan.value)) {
    // Remove any value that have not changed from the original defaultQueryParamsTemplate value it was set at
    let changedFlowValues = _.omitBy(rawOutput.value, function (v, k) {
      return JSON.stringify(originalFlowPlan.value[k]) === JSON.stringify(v);
    });

    if (_.isEmpty(changedFlowValues)) {
      return false;
    }
  }

  // If the flow plan isn't active disable the save
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
  let allFlowMissingFields = [];
  for (let action of _.flatten(Object.values(_.pick(flowActionTemplateObject.value, flowTypesMap.get(model.value.type).activeContainerList())))) {
    let actionMissingRequiredFields = validateAction(action);
    if (!_.isEmpty(actionMissingRequiredFields)) {
      allFlowMissingFields.push(actionMissingRequiredFields);
    }
  }

  return allFlowMissingFields.length == 0;
});

const flowPlanInProgress = () => {
  // If the flow plan is being edited and a value is changed return true that the flow plan should be saved
  if (!_.isEmpty(originalFlowPlan.value)) {
    // Remove any value that have not changed from the original defaultQueryParamsTemplate value it was set at
    let changedFlowValues = _.omitBy(rawOutput.value, function (v, k) {
      return JSON.stringify(originalFlowPlan.value[k]) === JSON.stringify(v);
    });

    if (_.isEmpty(changedFlowValues)) {
      return false;
    } else {
      return true;
    }
  }

  return model.value.active;
};

const items = ref([
  {
    label: "Save",
    icon: "fa-solid fa-hard-drive",
    isEnabled: isValidFlow,
    visible: true,
    command: () => {
      save(rawOutput.value);
    },
  },
]);

const validateSubscribe = computed(() => {
  // If the subscribe field is empty return "Missing subscriptions."
  if (_.isEmpty(model.value["subscribe"])) {
    return "Missing subscriptions.";
  }

  let checkIfSubscribeHasTopic = (key) =>
    model.value["subscribe"].some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  // If the subscribe field isn't empty but there isn't a topic return "Missing subscription topic."
  var isKeyPresent = checkIfSubscribeHasTopic("topic");

  if (!isKeyPresent) {
    return "Missing subscription topic.";
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

  // If the Publish Rules field isn't empty but there isn't a topic return "Missing publish rule topic."
  var isKeyPresent = checkIfPublishRulesHasTopic("topic");

  if (!isKeyPresent) {
    return "Missing publish rule topic.";
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

  // Check if the action name is missing if so add it to the list of missing required fields. The action name is required and should not allow the flow to be saved.
  if (_.isEmpty(action.name)) {
    missingFieldsInAction.push("name");
  }

  // Check if there are required schema fields, if so get the list of incomplete fields by comparing the required schema fields
  // with the keys of the user completed fields and add those to the list of missing required fields.
  missingFieldsInAction = _.concat(missingFieldsInAction, _.difference(requiredSchemaFields, completedFields));

  // All action names within a flow plan have to be unique.
  let duplicateActionNames = "";
  if (!_.isEmpty(action.name)) {
    let duplicateActionNamesInFlow = _.filter(_.flatten(Object.values(_.pick(flowActionTemplateObject.value, flowTypesMap.get(model.value.type).activeContainerList()))), { name: action.name });
    if (duplicateActionNamesInFlow.length > 1) {
      duplicateActionNames = `All action names within a Flow Plan have to be unique. Duplicate action name: ${action.name}.`;
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
  flowActionTemplateObject.value[flowActionTemplateMap.get(newActionValue["updatedAction"].flowActionType).activeContainer].splice(newActionValue["actionIndex"], 1, newActionValue["updatedAction"]);
  connectActions(newActionValue["updatedAction"].flowActionType);
};

const removeAction = (removeAction, index) => {
  flowActionTemplateObject.value[flowActionTemplateMap.get(removeAction.flowActionType).activeContainer].splice(index, 1);
  connectActions(removeAction.flowActionType);
};

const cloneAction = (clonedAction) => {
  let addNewClonedAction = JSON.parse(JSON.stringify(clonedAction));
  addNewClonedAction["id"] = _.uniqueId(addNewClonedAction["flowActionType"]);
  return JSON.parse(JSON.stringify(addNewClonedAction));
};

const validateNewAction = async (event) => {
  let addedEvent = _.get(event, "added", false);
  let movedEvent = _.get(event, "moved", false);
  let flowActionType = "";
  if (addedEvent) {
    flowActionType = addedEvent.element.flowActionType;
  } else if (movedEvent) {
    flowActionType = movedEvent.element.flowActionType;
  }
  connectActions(flowActionType);
};

// When there are multiple actions of a certain Action Type the order of the actions can be changed.
// If this happens we need to update the connections between each action. This
const actionOrderChanged = (event) => {
  let flowActionType = event.target.id;
  connectActions(flowActionType);
};

const actionTypesTree = ref({});
const flattenedActionsTypes = ref({});

const getLoadedActions = () => {
  for (const plugins of allActionsData.value) {
    for (const action of plugins["actions"]) {
      if (action.type === "TIMED_INGRESS") continue;

      // Reformatting each action.
      action["disableEdit"] = true;
      action["flowActionType"] = action["type"];
      action["type"] = action["name"];
      let displayName = action.name.split(".").pop();
      action["displayName"] = displayName;
      let coordinateGrouping = action.name.split(".").slice(0, -1).join(".");
      action["coordinateGrouping"] = coordinateGrouping;
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

      // Adding an flowActionType key to the actionTypesTree. Each root flowActionType key will hold the tree structure for that actionType.
      if (!Object.prototype.hasOwnProperty.call(actionTypesTree.value, action["flowActionType"])) {
        actionTypesTree.value[action["flowActionType"]] = [];
        flattenedActionsTypes.value[action["flowActionType"]] = [];
      }

      flattenedActionsTypes.value[action["flowActionType"]].push(action);

      // We next group all the actions into their respective plugins. We search in the actionTypesTree to see if the plugin
      // coordinateGrouping is already in the tree. If not we add it.
      let mavenCoordinateKey = actionTypesTree.value[action["flowActionType"]].find((x) => x.key === action["coordinateGrouping"]);
      // If no plugin coordinateGrouping is found we create it and go ahead and add the action to it.
      if (!mavenCoordinateKey) {
        let rootCoordinateGrouping = {};
        rootCoordinateGrouping["key"] = action["coordinateGrouping"];
        rootCoordinateGrouping["label"] = action["coordinateGrouping"];
        rootCoordinateGrouping["children"] = [];
        let pluginName = {};
        pluginName["key"] = action["displayName"];
        pluginName["label"] = action["displayName"];
        pluginName["filterField"] = action["displayName"];
        pluginName["data"] = [action];
        pluginName["type"] = "actions";
        rootCoordinateGrouping["children"].push(pluginName);
        actionTypesTree.value[action["flowActionType"]].push(rootCoordinateGrouping);
        actionTypesTree.value[action["flowActionType"]] = _.sortBy(actionTypesTree.value[action["flowActionType"]], "label");
      } else {
        // If plugin coordinateGrouping is found we add the action to it.
        let pluginName = {};
        pluginName["key"] = action["displayName"];
        pluginName["label"] = action["displayName"];
        pluginName["filterField"] = action["displayName"];
        pluginName["data"] = [action];
        pluginName["type"] = "actions";
        mavenCoordinateKey.children.push(pluginName);
        mavenCoordinateKey.children = _.sortBy(mavenCoordinateKey.children, "label");
      }
    }
  }
};

const rawOutput = computed(() => {
  if (!model.value.active) {
    return {};
  }
  let displayOutput = JSON.parse(JSON.stringify(model.value));

  for (const flowActionType of flowTypesMap.get(model.value.type).flowActionTypes) {
    if (!_.isEmpty(flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer])) {
      displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = JSON.parse(JSON.stringify(flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer]));
      if (schemaVisible.value) {
        displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
      } else {
        displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ ...attrs }) => _.pick(attrs, Object.keys(flowActionTemplateMap.get(flowActionType).selectTemplate[0])));
        if (flowActionTemplateMap.get(flowActionType).limit) {
          displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ schema, description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs)[0]; // eslint-disable-line @typescript-eslint/no-unused-vars
        } else {
          displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ schema, description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
        }
      }
    }
  }

  // Remove UI metakeys from output
  displayOutput = _.omit(displayOutput, ["active", "selectedFlowPlan"]);

  return displayOutput;
});

const actionTemplateClass = (element) => {
  return ["pi pi-plus-circle", "pr-2", "pt-1", { "added-action-color": !(_.findIndex(flowActionTemplateObject.value[flowActionTemplateMap.get(element.flowActionType).activeContainer], element) == -1) }];
};

const showSchema = () => {
  schemaVisible.value = !schemaVisible.value;
};

const flowPlanRawJsonDialogHide = () => {
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
  items: {
    type: "object",
    properties: {
      condition: {
        type: "string",
      },
      topic: {
        type: "string",
        enum: allTopics.value,
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
    defaultRule: {
      type: "object",
      default: {},
      properties: {
        defaultBehavior: {
          type: "string",
          enum: ["ERROR", "FILTER", "PUBLISH"],
          default: "ERROR",
        },
        topic: {
          type: "string",
          enum: allTopics.value,
        },
      },
    },
    matchingPolicy: {
      type: "string",
      enum: ["ALL_MATCHING", "FIRST_MATCHING"],
      default: "ALL_MATCHING",
    },
    rules: {
      type: "array",
      items: {
        type: "object",
        properties: {
          condition: {
            type: "string",
          },
          topic: {
            type: "string",
            enum: allTopics.value,
          },
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
    {
      type: "Control",
      scope: "#/properties/matchingPolicy",
    },
    {
      type: "Control",
      scope: "#/properties/rules",
      options: {
        detail: {
          type: "VerticalLayout",
          elements: [
            {
              type: "Control",
              scope: "#/properties/condition",
            },
            {
              type: "Control",
              scope: "#/properties/topic",
            },
          ],
        },
      },
    },
  ],
};
</script>

<style lang="scss">
@import "@/styles/pages/flow-plan-builder-page.scss";
</style>
