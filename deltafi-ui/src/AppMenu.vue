<template>
  <nav class="bg-light menu">
    <div class="pt-3">
      <ul class="nav flex-column">
        <div v-for="(item,i) in menuItems" :key="i">
          <div v-if="!item.hidden">
            <li class="nav-item">
              <span v-if="item.children" :class="menuItemClass(item)" @click.prevent="item.expand = !item.expand">
                <i :class="folderIcon(item)" />
                {{ item.name }}
              </span>
              <router-link v-else :to="item.path" :class="menuItemClass(item)">
                <i :class="item.icon" />
                {{ item.name }}
              </router-link>
            </li>
            <div v-for="child in item.children" :key="child" :class="{hidden: !item.expand, submenu: true}">
              <li v-if="!child.hidden" class="nav-item">
                <router-link v-if="child.path" :to="child.path" :class="menuItemClass(child, true)">
                  <i :class="child.icon" />
                  {{ child.name }}
                </router-link>
              </li>
            </div>
          </div>
        </div>
      </ul>
    </div>
  </nav>
</template>

<script>
import Router from "./router";

export default {
  name: "AppMenu",
  data() {
    return {
      routes: [],
      activePage: null,
      menuItems: [
        { name: "Dashboard", icon: "fas fa-desktop fa-fw", path: "/" },
        {
          name: "Metrics",
          expand: true,
          children: [
            {
              name: "System Metrics",
              icon: "far fa-chart-bar fa-fw",
              path: "/metrics/system",
            },
            {
              name: "Action Metrics",
              icon: "far fa-chart-bar fa-fw",
              path: "/metrics/actions",
              hidden: true,
            },
          ],
        },
        {
          name: "Configuration",
          expand: true,
          children: [
            {
              name: "System Properties",
              icon: "fas fa-cogs fa-fw",
              path: "/config/system",
            },
            {
              name: "Flows",
              icon: "fas fa-sitemap fa-fw",
              path: "/config/flow",
              hidden: true,
            },
            {
              name: "Plugins",
              icon: "fas fa-plus fa-fw",
              path: "/config/plugin",
              hidden: true,
            },
          ],
        },
        {
          name: "DeltaFiles",
          expand: true,
          children: [
            {
              name: "Search",
              icon: "fas fa-search fa-fw",
              path: "/deltafile/search",
            },
            {
              name: "Errors",
              icon: "fas fa-exclamation-circle fa-fw",
              path: "/errors",
            },
            {
              name: "Viewer",
              icon: "far fa-file fa-fw",
              path: "/deltafile/viewer/",
            },
          ],
        },
        { name: "Versions", icon: "fas fa-info-circle fa-fw", path: "/versions" },
      ],
    };
  },
  computed: {
    visibleMenuItems() {
      return this.menuItems.filter((item) => {
        return !item.hidden;
      });
    },
  },
  watch: {
    $route(to) {
      this.activePage = to.path;
    },
  },
  mounted() {
    this.routes = Router.getRoutes().sort((a, b) => {
      return a.meta.menuOrder - b.meta.menuOrder;
    });
  },
  methods: {
    folderIcon(item) {
      return item.expand ? "fas fa-angle-down fa-fw" : "fas fa-angle-right fa-fw";
    },
    menuItemClass(item, isChild = false) {
      let classes = ["nav-link noselect"];
      if (isChild) classes.push("indent");
      if (item.path === this.activePage) classes.push("active");
      if (item.children) classes.push("folder");
      return classes.join(" ");
    },
  },
};
</script>

<style scoped lang="scss">
  @import "./styles/app-menu.scss";
</style>