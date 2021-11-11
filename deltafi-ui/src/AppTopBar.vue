<template>
  <nav class="navbar navbar-dark sticky-top bg-dark flex-md-nowrap p-0 shadow">
    <router-link to="/" class="navbar-brand col-md-3 col-lg-2 mr-0 px-3">
      {{ uiConfig.title }}
    </router-link>
    <StatusBadge />
    <button
      class="navbar-toggler position-absolute d-md-none collapsed" type="button" data-toggle="collapse" data-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle navigation"
    >
      <span class="navbar-toggler-icon" />
    </button>
  </nav>
</template>

<script>
import StatusBadge from "@/components/StatusBadge.vue";
import { mapState } from "vuex";

export default {
  components: { StatusBadge },
  computed: mapState(["uiConfig"]),
};
</script>
<style scoped>
.navbar-brand {
  padding-top: 0.75rem;
  padding-bottom: 0.75rem;
  font-size: 1rem;
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
}

.navbar .navbar-toggler {
  top: 0.25rem;
  right: 1rem;
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}
</style>