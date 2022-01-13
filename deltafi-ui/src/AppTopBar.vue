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
import StatusBadge from "@/components/StatusBadge";
import { mapState } from "vuex";
import { useStore } from '@/store';
import { SidebarToggleActionTypes } from '@/store/modules/sidebarToggle/action-types';

export default {
  components: { StatusBadge },
  computed: {
    toggleSidebarIcon() {
      return this.sidebarHidden
        ? "pi pi-angle-double-right"
        : "pi pi-angle-double-left";
    },
    ...mapState({
      uiConfig: state => state.uiConfig.uiConfig,
      sidebarHidden: state => state.sidebarToggle.sidebarHidden
    })
  },
  methods: {
    toggleSidebar() {
      const store = useStore();
      store.dispatch(SidebarToggleActionTypes.TOGGLE_SIDEBAR);
    },
  },
};
</script>

<style scoped lang="scss">
  @import "@/styles/app-top-bar.scss";
</style>