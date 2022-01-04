<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Versions
      </h1>
    </div>
    <CollapsiblePanel header="Image Versions" class="table-panel">
      <DataTable :value="versions" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="loading">
        <Column field="app" header="App" :sortable="true" />
        <Column field="container" header="Container" :sortable="true" />
        <Column field="image.name" header="Image" :sortable="true" />
        <Column field="image.tag" header="Tag" :sortable="true" />
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script>
import ApiService from "@/service/ApiService";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

export default {
  name: "VersionsPage",
  components: {
    DataTable,
    Column,
    CollapsiblePanel
  },
  data() {
    return {
      loading: true,
      versions: []
    };
  },
  created() {
    this.apiService = new ApiService();
  },
  mounted() {
    this.fetchVersions();
  },
  methods: {
    async fetchVersions() {
      let response = await this.apiService.getVersions();
      this.versions = response.versions;
      this.loading = false;
    }
  },
  apiService: null
};
</script>
