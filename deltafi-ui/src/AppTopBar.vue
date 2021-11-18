<template>
  <nav class="navbar navbar-dark sticky-top bg-dark p-0 shadow">
    <div class="navbar-brand col mr-0 px-3">
      <div class="row">
        <div class="col title">
          {{ uiConfig.title }}
        </div>
        <div class="col text-right">
          <button v-tooltip.right="'Toggle sidebar menu'" class="navbar-toggler btn btn-sm" @click="toggleSidebar">
            <i :class="toggleSidebarIcon" />
          </button>
        </div>
      </div>
    </div>
    <status-badge />
  </nav>
</template>

<script>
import StatusBadge from "@/components/StatusBadge.vue";
import { mapState } from "vuex";

export default {
  components: { StatusBadge },
  computed: {
    toggleSidebarIcon() {
      return this.sidebarHidden
        ? "pi pi-angle-double-right"
        : "pi pi-angle-double-left";
    },
    ...mapState(["uiConfig", "sidebarHidden"]),
  },
  methods: {
    toggleSidebar() {
      this.$store.dispatch("toggleSidebar");
    },
  },
};
</script>

<style scoped lang="scss">
  @import "./styles/app-top-bar.scss";
</style>