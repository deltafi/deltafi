<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        DeltaFile Search
      </h1>
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Calendar
          id="startDateTime"
          v-model="startTimeDate"
          selection-mode="single"
          :inline="false"
          :show-time="true"
          :manual-input="false"
          hour-format="12"
          input-class="deltafi-input-field ml-3"
        />
        <span class="mt-1 ml-3">&mdash;</span>
        <Calendar
          id="endDateTime"
          v-model="endTimeDate"
          selection-mode="single"
          :inline="false"
          :show-time="true"
          :manual-input="false"
          hour-format="12"
          input-class="deltafi-input-field ml-3"
        />
        <Button class="p-button-sm p-button-secondary p-button-outlined ml-3" @click="fetchDeltaFilesData()">
          Search
        </Button>
      </div>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <CollapsiblePanel header="Search Options" :collapsed="true">
          <template #icons>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="toggle">
              <span class="fas fa-cog" />
            </Button>
            <Menu id="config_menu" ref="menu" :model="items" :popup="true" />
          </template>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right">
              <label for="fileNameId">Filename:</label>
            </div>
            <div class="col-md-auto">
              <Dropdown
                id="fileNameId"
                v-model="fileNameOptionSelected"
                placeholder="Select a File Name"
                :options="fileNameOptions"
                option-label="name"
                :filter="true"
                :show-clear="true"
                class="deltafi-input-field min-width"
              />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right align-text-bottom">
              <label for="flowId">Flow:</label>
            </div>
            <div class="col-md-auto">
              <Dropdown
                id="flowId"
                v-model="flowOptionSelected"
                placeholder="Select a Flow"
                :options="flowOptions"
                show-clear
                :editable="false"
                class="deltafi-input-field min-width"
              />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right">
              <label for="stageId">Stage:</label>
            </div>
            <div class="col-md-auto">
              <Dropdown
                id="stageId"
                v-model="stageOptionSelected"
                placeholder="Select a Stage"
                :options="stageOptions"
                option-label="name"
                show-clear
                :editable="false"
                class="deltafi-input-field min-width"
              />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right pl-0 pr-3">
              <label for="actionTypeId">Action Type:</label>
            </div>
            <div class="col-md-auto">
              <Dropdown
                id="actionTypeId"
                v-model="actionTypeOptionSelected"
                placeholder="Select an Action Type"
                :options="actionTypeOptions"
                option-label="name"
                :filter="true"
                :show-clear="true"
                class="deltafi-input-field min-width"
              />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1" />
            <div class="col-1">
              <span class="float-left">
                <i v-badge="recordCount" class="pi align-top p-text-secondary float-right icon-index" style="font-size: 2rem" />
                <Button type="button" label="Search" class="p-button-sm p-button-secondary p-button-outlined float-right" @click="fetchDeltaFilesData()" />
              </span>
            </div>
          </div>
        </CollapsiblePanel>
      </div>
    </div>
    <CollapsiblePanel header="DeltaFiles" class="table-panel">
      <DataTable
        :value="results"
        striped-rows
        class="p-datatable p-datatable-sm p-datatable-gridlines"
        :loading="loading"
        :paginator="totalRecords > 0"
        :rows="10"
        :lazy="true"
        :rows-per-page-options="[10,20,50,100]"
        :total-records="totalRecords"
        :always-show-paginator="true"
        paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        current-page-report-template="Showing {first} to {last} of {totalRecords} DeltaFiles"
        @page="onPage($event)"
        @sort="onSort($event)"
      >
        <template #empty>
          No DeltaFiles in the selected time range.
        </template>
        <template #loading>
          Loading DeltaFiles. Please wait.
        </template>
        <Column field="did" header="DID (UUID)">
          <template #body="tData">
            <router-link class="monospace" :to="{path: 'viewer/' + tData.data.did}">
              {{ tData.data.did }}
            </router-link>
          </template>
        </Column>
        <Column field="sourceInfo.filename" header="Filename" :sortable="true" />
        <Column field="sourceInfo.flow" header="Flow" :sortable="true" />
        <Column field="stage" header="Stage" :sortable="true" />
        <Column field="created" header="Created" :sortable="true" />
        <Column field="modified" header="Modified" :sortable="true" />
        <Column field="elapsed" header="Elapsed" :sortable="false">
          <template #body="row">
            {{ row.data.elapsed }}
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script>
import Button from 'primevue/button';
import Calendar from 'primevue/calendar';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import Dropdown from 'primevue/dropdown';
import GraphQLService from "@/service/GraphQLService";
import Menu from "primevue/menu";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import { UtilFunctions } from "@/utils/UtilFunctions";

var currentDateObj = new Date();
var numberOfMlSeconds = currentDateObj.getTime();
var addMlSeconds = 60 * 60 * 1000;
var newDateObj = new Date(numberOfMlSeconds - addMlSeconds);
currentDateObj = new Date(numberOfMlSeconds + addMlSeconds);

export default {
  name: "SearchPage",
  components: {
    Button,
    Calendar,
    Column,
    DataTable,
    Dropdown,
    Menu,
    CollapsiblePanel,
  },
  data() {
    return {
      items: [{
        label: 'Options',
        items: [{
          label: 'Clear Options',
          icon: 'fas fa-times',
          command: () => {
            this.actionTypeOptionSelected = null;
            this.fileNameOptionSelected = null;
            this.flowOptionSelected = null;
            this.stageOptionSelected = null;
          }
        }]
      }],
      loading: true,
      tableData: [],
      expandedRows: [],
      startTimeDate: newDateObj,
      endTimeDate: currentDateObj,
      configTypeNames: [],
      actionName: null,
      actionTypeOptions: [],
      actionTypeOptionSelected: null,
      fileName: null,
      fileNameOptions: [],
      fileNameOptionSelected: null,
      flowName: null,
      flowOptions: [],
      flowOptionSelected: null,
      stageName: null,
      stageOptions: [],
      stageOptionSelected: null,
      recordCount:"",
      totalRecords: 0,
      offset: 0,
      perPage: 10,
      sortField: "modified",
      sortDirection: "DESC",
    };
  },
  computed: {
    results() {
      return this.tableData.map(row => {
        const timeElapsed = (new Date(row.modified) - new Date(row.created));
        return {
          ...row,
          elapsed: this.utilFunctions.duration(timeElapsed)
        }
      });
    }
  },
  watch: {
    startTimeDate() {
      this.fetchAdvancedOptions();
      this.fetchRecordCount();
    },
    endTimeDate() {
      this.fetchAdvancedOptions();
      this.fetchRecordCount();
    },
    fileNameOptionSelected() {
      this.fetchRecordCount();
    },
    actionTypeOptionSelected() {
      this.fetchRecordCount();
    },
    flowOptionSelected() {
      this.fetchRecordCount();
    },
    stageOptionSelected() {
      this.fetchRecordCount();
    }
  },
  created() {
    this.utilFunctions = new UtilFunctions();
    this.graphQLService = new GraphQLService();
    this.fetchDeltaFilesData();
    this.fetchAdvancedOptions();
    this.fetchConfigTypes();
    this.fetchStages();
  },
  methods: {
    toggle(event) {
      this.$refs.menu.toggle(event);
    },
    async fetchAdvancedOptions() {
      this.fileNameDataArray = [];
      let fetchAdvancedOptions = await this.graphQLService.getDeltaFiFileNames(this.startTimeDate, this.endTimeDate, this.fileName, this.stageName, this.actionName, this.flowName);
      let deltaFilesObjectsArray = fetchAdvancedOptions.data.deltaFiles.deltaFiles;
      for (const deltaFiObject of deltaFilesObjectsArray) {
          this.fileNameDataArray.push({"name" : deltaFiObject.sourceInfo.filename});
      }

      this.fileNameOptions = this.dedupArrayOfObjects(this.fileNameDataArray);
    },
    async fetchConfigTypes() {
      var flowTypes = [];
      var actionTypes = [];
      let enumsConfigTypes = await this.graphQLService.getEnumValuesByEnumType("ConfigType");
      this.configTypeNames = enumsConfigTypes.data.__type.enumValues;

      // Convert array of ConfigType objects with
      let result = this.configTypeNames.map(a => a.name);
      for (const element of result) {
        if(element.includes("FLOW")) {
          flowTypes.push(element);
        } else {
          actionTypes.push(element);
        }
      }

      this.fetchActions(actionTypes);
      this.fetchFlows(flowTypes);
    },
    async fetchActions(actionTypes) {
      for (const actionType of actionTypes) {
        let actionData = await this.graphQLService.getConfigByType(actionType);
        let actionDataValues = actionData.data.deltaFiConfigs;
        this.actionTypeOptions = this.actionTypeOptions.concat(actionDataValues);
        this.actionTypeOptions = [...new Set(this.actionTypeOptions)];
      }
    },
    async fetchFlows(flowTypes) {
      for (const flowType of flowTypes) {
        let flowData = await this.graphQLService.getConfigByType(flowType);
        let flowDataValues = flowData.data.deltaFiConfigs;
        this.flowOptions = this.flowOptions.concat(flowDataValues.map(a => a.name));
        //this.flowNames = this.flowNames.concat(flowDataValues);
        this.flowOptions = [...new Set(this.flowOptions)];
      }
    },
    async fetchStages() {
    let enumsStageTypes = await this.graphQLService.getEnumValuesByEnumType("DeltaFileStage");
      this.stageOptions = enumsStageTypes.data.__type.enumValues;
    },
    async fetchDeltaFilesData() {
      this.setQueryParams();

      this.loading = true;
      this.fetchRecordCount();
      let data = await this.graphQLService.getDeltaFileSearchData(this.startTimeDate, this.endTimeDate, this.offset, this.perPage, this.sortField, this.sortDirection, this.fileName, this.stageName, this.actionName, this.flowName);
      this.tableData = data.data.deltaFiles.deltaFiles;
      this.loading = false;
      this.totalRecords = data.data.deltaFiles.totalCount;
    },
    async fetchRecordCount() {
      this.setQueryParams();

      let fetchRecordCount = await this.graphQLService.getRecordCount(this.startTimeDate, this.endTimeDate, this.fileName, this.stageName, this.actionName, this.flowName);
      this.recordCount = fetchRecordCount.data.deltaFiles.totalCount.toString();
    },
    setQueryParams() {
      this.fileName = this.fileNameOptionSelected ? this.fileNameOptionSelected.name : null;
      this.stageName = this.stageOptionSelected ? this.stageOptionSelected.name : null;
      this.actionName = this.actionTypeOptionSelected ? this.actionTypeOptionSelected.name : null;
      this.flowName = this.flowOptionSelected ? this.flowOptionSelected : null;
    },
    dedupArrayOfObjects(values) {
     return values.filter((value, index, self) =>
        index === self.findIndex((t) => (
          t.name === value.name
        ))
      )
    },
    onSort(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.sortField = event.sortField;
      this.sortDirection = event.sortOrder > 0 ? "DESC" : "ASC";
      this.fetchDeltaFilesData();
    },
    onPage(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.fetchDeltaFilesData();
    },
  },
};
</script>

<style lang="scss">
  @import "@/styles/pages/deltafile-search-page.scss";
</style>