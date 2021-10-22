<template>
  <nav class="col-md-3 col-lg-2 d-md-block bg-light sidebar collapse">
    <div class="sidebar-sticky pt-3">
      <ul class="nav flex-column">
        <li class="nav-item" v-for="route in routes" :key="route.path">
          <router-link v-bind:to="route.path" class="nav-link">
            <i v-bind:class="route.meta.menuIconClass"></i>
            {{ route.name }}
          </router-link>
        </li>
      </ul>
    </div>
  </nav>
</template>

<script>
import Router from './router';

export default {
  name: "AppMenu",
  data() {
    return {
      routes: []
    }
  },
  mounted() {
    this.routes = Router.getRoutes();
  }
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 100; /* Behind the navbar */
  padding: 48px 0 0; /* Height of navbar */
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, .1);
}

@media (max-width: 767.98px) {
  .sidebar {
    top: 5rem;
  }
}

.sidebar-sticky {
  position: relative;
  top: 0;
  height: calc(100vh - 48px);
  padding-top: .5rem;
  overflow-x: hidden;
  overflow-y: auto; /* Scrollable contents if viewport is shorter than content. */
}

@supports ((position: -webkit-sticky) or (position: sticky)) {
  .sidebar-sticky {
    position: -webkit-sticky;
    position: sticky;
  }
}

.sidebar .nav-link {
  font-weight: 500;
  color: #333;
}

.sidebar .nav-link .pi {
  margin-right: 4px;
  color: #999;
}

.sidebar .nav-link.active {
  color: #007bff;
}

.sidebar-heading {
  font-size: .75rem;
  text-transform: uppercase;
}
</style>