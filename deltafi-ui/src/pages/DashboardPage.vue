<template>
  <div class="dashboard">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Dashboard
      </h1>
    </div>
    <div class="row pl-2 pr-2">
      <div class="col-md-12 col-lg-6 col-xl-4 pl-2 pr-2">
        <CollapsiblePanel header="External Links" class="links-panel pl-0">
          <div class="list-group list-group-flush">
            <a v-for="link in externalLinks" :key="link" :href="link.url" target="_blank" class="list-group-item list-group-item-action">
              <div class="d-flex w-100 justify-content-between">
                <h5 class="mb-0">{{ link.name }}</h5>
                <i class="fas fa-external-link-alt" />
              </div>
              <small class="mb-1 text-muted">{{ link.description }}</small>
            </a>
          </div>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from "vuex";
import CollapsiblePanel from "@/components/CollapsiblePanel";

export default {
  name: "DashboardPage",
  components: {
    CollapsiblePanel
  },
  computed: {
    ...mapGetters(["externalLinks"]),
  }
};
</script>

<style lang="scss">
  @import "@/styles/pages/dashboard-page.scss";
</style>