<template>
  <div class="dashboard">
    <PageHeader heading="Dashboard" />
    <div class="row pl-2 pr-2">
      <div class="col-md-12 col-lg-6 col-xl-4 pl-2 pr-2">
        <CollapsiblePanel header="External Links" class="links-panel pl-0">
          <div class="list-group list-group-flush">
            <a v-for="link in externalLinks" :key="link" :href="link.url" target="_blank" class="list-group-item list-group-item-action">
              <div class="d-flex w-100 justify-content-between">
                <strong class="mb-0">{{ link.name }}</strong>
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
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useUiConfig from "@/composables/useUiConfig";
import { computed } from "vue";

export default {
  components: { CollapsiblePanel },
  setup() {
    const { uiConfig } = useUiConfig();

    const externalLinks = computed(() => {
      return uiConfig.value.dashboard.links;
    });

    return {
      externalLinks,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/pages/dashboard-page.scss";
</style>