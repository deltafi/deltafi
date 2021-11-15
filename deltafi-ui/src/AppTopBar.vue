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

<style scoped>
.navbar-brand {
  padding-top: 0.6rem;
  padding-bottom: 0.6rem;
  font-size: 1.1rem;
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
  -ms-flex: 0 0 300px;
  flex: 0 0 300px;
}

.navbar-brand .title {
  align-items: center;
  display: flex;
  font-weight: 300;
}

.navbar-brand .navbar-toggler:focus {
  outline: none;
  box-shadow: none;
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}
</style>