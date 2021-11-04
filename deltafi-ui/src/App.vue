<template>
  <div class="app">
    <AppTopBar />
    <div class="container-fluid">
      <div class="row">
        <div id="sidebarMenu">
          <AppMenu />
        </div>
        <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-md-4">
          <router-view />
        </main>
      </div>
    </div>
  </div>
</template>

<script>
import AppTopBar from './AppTopBar.vue';
import AppMenu from './AppMenu.vue';

export default {
  name: "App",
  components: {
    'AppTopBar': AppTopBar,
    'AppMenu': AppMenu
  },
  watch: {
    $route: {
      immediate: true,
      handler(to) {
        document.title = to.meta.title || 'DeltaFi';
      }
    },
  }
}
</script>
