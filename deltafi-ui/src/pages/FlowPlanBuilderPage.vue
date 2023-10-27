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
    <PageHeader :heading="`test`">
      <template #header>
        <div class="align-items-center btn-group">
          <h2 class="mb-0">{{ flowPlanHeader }}</h2>
          <div v-if="model.active" class="btn-group">
            <DialogTemplate component-name="flowBuilder/FlowConfigurationDialog" :header="`Edit ${model.name}`" dialog-width="25vw" model-position="center" :data-prop="model" edit-flow-plan @create-flow-plan="setFlowValues">
              <Button v-if="!editExistingFlowPlan" v-tooltip.top="`Edit Name and Description`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary ml-2" />
            </DialogTemplate>
          </div>
        </div>
      </template>
    </PageHeader>
    <div v-if="model.active">
      <div class="row p-2">
        <div v-for="action of flowTypesMap.get(model.type).flowActionTypes" :key="action" :class="getColSize(action)">
          <div>
            <Panel :header="_.startCase(flowActionTemplateMap.get(action).activeContainer)" class="table-panel">
              <template #icons>
                <button class="p-panel-header-icon p-link" @click="viewActionTreeMenu($event, action)">
                  <span class="pi pi-plus-circle"></span>
                </button>
              </template>
              <div class="action-panel-content p-2">
                <div v-if="flowActionTemplateObject[flowActionTemplateMap.get(action).activeContainer].length == 0" class="empty-action pt-2 mb-n3">No {{ _.startCase(flowActionTemplateMap.get(action).activeContainer) }}</div>
                <draggable :id="action" v-model="flowActionTemplateObject[flowActionTemplateMap.get(action).activeContainer]" item-key="id" :sort="true" :group="action" ghost-class="action-transition-layout" drag-class="action-transition-layout" class="dragArea panel-horizontal-wrap pb-2 pt-3" @change="validateNewAction" @move="actionOrderChanged">
                  <template #item="{ element, index }">
                    <div :id="element.id" class="action-layout border border-dark rounded mx-2 my-4 p-overlay-badge">
                      <Badge v-if="!isValidAction(element)" v-tooltip.left="{ value: `${validateAction(element)}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
                      <div class="d-flex align-items-center justify-content-between">
                        <InputText v-model="element.name" :class="'inputtext-border-remove pl-0'" placeholder="Action Name Required" />
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
    </div>
    <HoverSaveButton v-if="model.active" target="window" :model="items" />
    <OverlayPanel ref="actionsOverlayPanel" class="flow-plan-builder-page-overlay" append-to="body" dismissable show-close-icon :style="{ width: '22%' }">
      <Tree ref="actionsTreeRef" v-model:expandedKeys="expandedKeys" :value="actionsTree" :filter="true" filter-mode="strict" filter-by="filterField">
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
              </div>
            </template>
          </draggable>
        </template>
      </Tree>
    </OverlayPanel>
    <DialogTemplate component-name="flowBuilder/FlowConfigurationDialog" header="Create New Flow Plan" dialog-width="25vw" model-position="center" :closable="false" :disable-model="true" :data-prop="model" @create-flow-plan="createFlowPlan">
      <span id="CreateFlowPlan" />
    </DialogTemplate>
  </div>
</template>

<script setup>
import HoverSaveButton from "@/components/flowBuilder/HoverSaveButton.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowActions from "@/composables/useFlowActions";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, nextTick, onBeforeMount, ref, watch } from "vue";
import { useResizeObserver, useStorage, StorageSerializers } from "@vueuse/core";
import { useRouter } from "vue-router";

import Badge from "primevue/badge";
import Button from "primevue/button";
import Divider from "primevue/divider";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import OverlayPanel from "primevue/overlaypanel";
import Tree from "primevue/tree";
import draggable from "vuedraggable";

import { jsPlumb } from "jsplumb";
import $ from "jquery";
import _ from "lodash";

const { getPluginActionSchema } = useFlowActions();
const actionsOverlayPanel = ref();
const actionsTreeRef = ref(null);

const notify = useNotifications();
const router = useRouter();

const { getAllFlowPlans, saveTransformFlowPlan, saveNormalizeFlowPlan, saveEgressFlowPlan, saveEnrichFlowPlan } = useFlowPlanQueryBuilder();

const allActionsData = ref({});

const expandedKeys = ref({});

const flowPlanBuilderPage = ref(null);
const pageWidthResizeObserver = ref(null);
const editExistingFlowPlan = ref(false);
const originalFlowPlan = ref(null);

// The useResizeObserver determins if the sidebar has been collapsed or expanded.
// If either has occur we redo the connections between all actions.
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

// This watch on filterValue expands the tree to the full depth for values
// that match the search filter in the action tree.
watch(
  () => actionsTreeRef.value?.filterValue,
  (data) => {
    // The tree components filteredValue is a very tempermental computed value.
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
  // for linking the draggable actions to their repsentative flowActionType.
  flowActionTypeGroup.value = flowActionType;
  // The actionsTree is the value used to dynamically provide the array of actions for each flowActionType.
  actionsTree.value = actionTypesTree.value[flowActionType];
  actionsOverlayPanel.value.toggle(event);
};

const flowTemplate = {
  type: null,
  active: false,
  name: null,
  description: null,
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
};

const loadActionTemplate = {
  flowActionType: "LOAD",
  ...defaultActionKeys,
};

const domainActionsTemplate = {
  flowActionType: "DOMAIN",
  ...defaultActionKeys,
  requiresDomains: [],
};

const enrichActionsTemplate = {
  flowActionType: "ENRICH",
  ...defaultActionKeys,
  requiresDomains: [],
  requiresEnrichments: [],
  requiresMetadataKeyValues: [],
};

const formatActionTemplate = {
  flowActionType: "FORMAT",
  ...defaultActionKeys,
  requiresDomains: [],
  requiresEnrichments: [],
};

const validateActionsTemplate = {
  flowActionType: "VALIDATE",
  ...defaultActionKeys,
};

const egressActionTemplate = {
  flowActionType: "EGRESS",
  ...defaultActionKeys,
};

const flowPlan = ref(JSON.parse(JSON.stringify(flowTemplate)));

const flowTypesMap = new Map([
  ["TRANSFORM", { flowActionTypes: ["TRANSFORM", "EGRESS"] }],
  ["NORMALIZE", { flowActionTypes: ["TRANSFORM", "LOAD"] }],
  ["ENRICH", { flowActionTypes: ["DOMAIN", "ENRICH"] }],
  ["EGRESS", { flowActionTypes: ["FORMAT", "VALIDATE", "EGRESS"] }],
]);

const flowActionTemplateMap = new Map([
  ["TRANSFORM", { selectTemplate: [transformActionsTemplate], activeContainer: "transformActions", limit: false }],
  ["LOAD", { selectTemplate: [loadActionTemplate], activeContainer: "loadAction", limit: true }],
  ["DOMAIN", { selectTemplate: [domainActionsTemplate], activeContainer: "domainActions", limit: false }],
  ["ENRICH", { selectTemplate: [enrichActionsTemplate], activeContainer: "enrichActions", limit: false }],
  ["FORMAT", { selectTemplate: [formatActionTemplate], activeContainer: "formatAction", limit: true }],
  ["VALIDATE", { selectTemplate: [validateActionsTemplate], activeContainer: "validateActions", limit: false }],
  ["EGRESS", { selectTemplate: [egressActionTemplate], activeContainer: "egressAction", limit: true }],
]);

const flowActionTemplateObject = ref({
  transformActions: [],
  loadAction: [],
  domainActions: [],
  enrichActions: [],
  formatAction: [],
  validateActions: [],
  egressAction: [],
});

const originalFlowActionTemplateObject = JSON.parse(JSON.stringify(flowActionTemplateObject.value));

const schemaVisable = ref(false);

onBeforeMount(async () => {
  await fetchData();

  if (!_.isEmpty(_.get(linkedFlowPlan.value, "flowPlanParams", null))) {
    let response = await getAllFlowPlans();
    let allFlowPlans = response.data.getAllFlowPlans;
    if (linkedFlowPlan.value.flowPlanParams.editExistingFlow) {
      editExistingFlowPlan.value = true;
      let flowInfo = {};
      flowInfo["type"] = _.toUpper(linkedFlowPlan.value.flowPlanParams.type);
      flowInfo["name"] = linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName;
      flowInfo["description"] = linkedFlowPlan.value.flowPlanParams.selectedFlowPlan.description;
      flowInfo["selectedFlowPlan"] = _.find(allFlowPlans[`${_.toLower(linkedFlowPlan.value.flowPlanParams.type)}Plans`], { name: linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName });
      await createFlowPlan(flowInfo);
      originalFlowPlan.value = rawOutput.value;
    } else {
      model.value.type = _.toUpper(linkedFlowPlan.value.flowPlanParams.type);
      model.value.selectedFlowPlan = _.find(allFlowPlans[`${_.toLower(model.value.type)}Plans`], { name: linkedFlowPlan.value.flowPlanParams.selectedFlowPlanName });
      document.getElementById("CreateFlowPlan").click();
    }
    linkedFlowPlan.value = null;
  } else {
    document.getElementById("CreateFlowPlan").click();
  }
});

const fetchData = async () => {
  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;
  getLoadedActions();
};

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

const flowActionTypeGroup = ref("");
const actionsTree = ref([]);
const flowActionSpecificJsPlumbInstance = ref({});

const flowPlanHeader = computed(() => {
  let header = model.value.name ? `Flow Plan Builder - ${model.value.name}` : "Flow Plan Builder";
  return header;
});

const getColSize = (flowActionType) => {
  if (flowTypesMap.get(model.value.type).flowActionTypes.length <= 2) {
    return !flowActionTemplateMap.get(flowActionType).limit ? "col pl-2 pr-1" : "col-4 pl-2 pr-1";
  }
  return "col pl-2 pr-1";
};

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

const save = async (rawFlow) => {
  let response = null;
  if (model.value.type === "TRANSFORM") {
    response = await saveTransformFlowPlan(rawFlow);
  } else if (model.value.type === "NORMALIZE") {
    response = await saveNormalizeFlowPlan(rawFlow);
  } else if (model.value.type === "ENRICH") {
    response = await saveEnrichFlowPlan(rawFlow);
  } else if (model.value.type === "EGRESS") {
    response = await saveEgressFlowPlan(rawFlow);
  }
  if (response !== undefined) {
    notify.success(`${response.data[`save${_.capitalize(model.value.type)}FlowPlan`].name} Flow Plan Saved`);
    model.value.active = null;
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
  // present for that flowActionType. This allows for resetting of that specific jsPlumb instance everytime we
  // need to redraw the connection.
  if (!_.get(flowActionSpecificJsPlumbInstance.value, flowActionType, null)) {
    flowActionSpecificJsPlumbInstance.value[flowActionType] = jsPlumb.getInstance();
  }

  let plumbIns = flowActionSpecificJsPlumbInstance.value[flowActionType];

  plumbIns.ready(function () {
    // Reset the action connects so we can rewdraw them.
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
  // Remove all the actions that dont have an id.
  _.remove(actionsInPanel, { id: "" });

  // Connect all the actions in the panel
  for (let i = 0; i < actionsInPanel.length - 1; i++) {
    const current = actionsInPanel[i];
    const next = actionsInPanel[i + 1];

    let anchorType = [];
    // If the offesetTop of both the current and next are the same that the action hasnt wrapped to a new
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
  if (flowActionTemplateMap.get(flowActionType).limit) {
    if (flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer].length > 0) {
      return;
    }
  }

  flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer].push(addNewAction);
  connectActions(flowActionType);
};

const isValidFlow = computed(() => {
  if (!_.isEmpty(originalFlowPlan.value)) {
    // Remove any value that have not changed from the original defaultQueryParamsTemplate value it was set at
    let changedFlowValues = _.omitBy(rawOutput.value, function (v, k) {
      return JSON.stringify(originalFlowPlan.value[k]) === JSON.stringify(v);
    });

    if (_.isEmpty(changedFlowValues)) {
      return false;
    }
  }

  if (_.isEmpty(model.value.type)) {
    return false;
  }
  let allFlowMissingFields = [];
  if (!_.isEmpty(displayFlowMissingValuesBadge())) {
    allFlowMissingFields.push(displayFlowMissingValuesBadge());
  }

  for (let flowActionType of flowTypesMap.get(model.value.type).flowActionTypes) {
    for (let action of flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer]) {
      let actionMissingRequiredFields = validateAction(action);
      if (!_.isEmpty(actionMissingRequiredFields)) {
        allFlowMissingFields.push(actionMissingRequiredFields);
      }
    }
  }

  return allFlowMissingFields.length == 0;
});

const items = ref([
  {
    label: "Save",
    icon: "fa-solid fa-hard-drive",
    isEnabled: isValidFlow,

    command: () => {
      save(rawOutput.value);
    },
  },
]);

const displayFlowMissingValuesBadge = () => {
  let allFlowMissingFields = [];
  if (_.isEmpty(model.value.name)) {
    allFlowMissingFields.push("Name");
  }

  if (_.isEmpty(model.value.description)) {
    allFlowMissingFields.push("Description");
  }

  if (_.isEmpty(allFlowMissingFields)) {
    return null;
  }

  return _.capitalize(`Flow missing required fields: ${allFlowMissingFields.join(", ")}`);
};

const isValidAction = (action) => {
  if (_.isEmpty(validateAction(action))) {
    return true;
  }
  return false;
};

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

  // Check if there are required schema fields, if so get the list of incompleted fields by comparing the required schema fields
  // with the keys of the user completed fields and add those to the list of missing required fields.
  missingFieldsInAction = _.concat(missingFieldsInAction, _.difference(requiredSchemaFields, completedFields));

  // Actions within the same ActionType group cannot have the same name.
  let duplicateActionNames = "";
  if (!_.isEmpty(action.name)) {
    let duplicateActionNamesInFlow = _.filter(flowActionTemplateObject.value[flowActionTemplateMap.get(action.flowActionType).activeContainer], { name: action.name, type: action.type });
    if (duplicateActionNamesInFlow.length > 1) {
      duplicateActionNames = `Duplicate action name: ${action.name} for action type: ${action.type}`;
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
    if (flowActionTemplateMap.get(flowActionType).limit) {
      if (flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer].length > 1) {
        flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer].pop();
      }
    }
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
      let corrdinateGrouping = action.name.split(".").slice(0, -1).join(".");
      action["corrdinateGrouping"] = corrdinateGrouping;
      action["name"] = "";
      action["parameters"] = {};

      // Adding an flowActionType key to the actionTypesTree. Each root flowActionType key will hold the tree structure for that actionType.
      if (!Object.prototype.hasOwnProperty.call(actionTypesTree.value, action["flowActionType"])) {
        actionTypesTree.value[action["flowActionType"]] = [];
        flattenedActionsTypes.value[action["flowActionType"]] = [];
      }

      flattenedActionsTypes.value[action["flowActionType"]].push(action);

      // We next group all the actions into their respective plugins. We search in the actionTypesTree to see if the plugin
      // corridinateGrouping is already in the tree. If not we add it.
      let mavenCorrdinateKey = actionTypesTree.value[action["flowActionType"]].find((x) => x.key === action["corrdinateGrouping"]);
      // If no plugin corrdinateGrouping is found we create it and go ahead and add the action to it.
      if (!mavenCorrdinateKey) {
        let rootCorrdinateGrouping = {};
        rootCorrdinateGrouping["key"] = action["corrdinateGrouping"];
        rootCorrdinateGrouping["label"] = action["corrdinateGrouping"];
        rootCorrdinateGrouping["children"] = [];
        let pluginName = {};
        pluginName["key"] = action["displayName"];
        pluginName["label"] = action["displayName"];
        pluginName["filterField"] = action["displayName"];
        pluginName["data"] = [action];
        pluginName["type"] = "actions";
        rootCorrdinateGrouping["children"].push(pluginName);
        actionTypesTree.value[action["flowActionType"]].push(rootCorrdinateGrouping);
        actionTypesTree.value[action["flowActionType"]] = _.sortBy(actionTypesTree.value[action["flowActionType"]], "label");
      } else {
        // If plugin corrdinateGrouping is found we add the action to it.
        let pluginName = {};
        pluginName["key"] = action["displayName"];
        pluginName["label"] = action["displayName"];
        pluginName["filterField"] = action["displayName"];
        pluginName["data"] = [action];
        pluginName["type"] = "actions";
        mavenCorrdinateKey.children.push(pluginName);
        mavenCorrdinateKey.children = _.sortBy(mavenCorrdinateKey.children, "label");
      }
    }
  }
};

const rawOutput = computed(() => {
  if (!_.isEmpty(model.value.active)) {
    return {};
  }
  let displayOutput = JSON.parse(JSON.stringify(model.value));

  for (const flowActionType of flowTypesMap.get(model.value.type).flowActionTypes) {
    if (!_.isEmpty(flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer])) {
      displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = JSON.parse(JSON.stringify(flowActionTemplateObject.value[flowActionTemplateMap.get(flowActionType).activeContainer]));
      if (schemaVisable.value) {
        displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
      } else {
        if (flowActionTemplateMap.get(flowActionType).limit) {
          displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ ...attrs }) => _.pick(attrs, Object.keys(flowActionTemplateMap.get(flowActionType).selectTemplate[0])));
          displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ schema, description, flowActionType, disableEdit, ...keepAttrs }) => keepAttrs)[0]; // eslint-disable-line @typescript-eslint/no-unused-vars
        } else {
          displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer] = displayOutput[flowActionTemplateMap.get(flowActionType).activeContainer].map(({ ...attrs }) => _.pick(attrs, Object.keys(flowActionTemplateMap.get(flowActionType).selectTemplate[0])));
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
</script>

<style lang="scss">
@import "@/styles/pages/flow-plan-builder-page.scss";
</style>
