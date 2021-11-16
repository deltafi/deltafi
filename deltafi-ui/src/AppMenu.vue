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
        { name: "Dashboard", icon: "pi pi-desktop", path: "/" },
        {
          name: "Metrics",
          expand: true,
          children: [
            {
              name: "System Metrics",
              icon: "pi pi-chart-bar",
              path: "/metrics/system",
            },
            {
              name: "Action Metrics",
              icon: "pi pi-chart-line",
              path: "/metrics/actions",
              hidden: true,
            },
          ],
        },
        {
          name: "Configuration",
          expand: true,
          hidden: true,
          children: [
            {
              name: "System",
              icon: "pi pi-cog",
              path: "/config/system",
              hidden: true,
            },
            {
              name: "Flows",
              icon: "pi pi-sitemap",
              path: "/config/flow",
              hidden: true,
            },
            {
              name: "Plugins",
              icon: "pi pi-plus",
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
              icon: "pi pi-search",
              path: "/deltafiles",
              hidden: true,
            },
            {
              name: "Errors",
              icon: "pi pi-times-circle",
              path: "/errors",
            },
          ],
        },
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
      return item.expand ? "pi pi-angle-down" : "pi pi-angle-right";
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

<style scoped>
.menu {
  position: fixed;
  top: 0;
  bottom: 0;
  z-index: 100; /* Behind the navbar */
  padding: 48px 0 0; /* Height of navbar */
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.1);
  width: 300px;
}

.menu .nav-link {
  font-weight: 500;
  color: #333;
}

.menu .nav-link.folder {
  font-weight: 300;
  color: #999;
  cursor: pointer;
}

.menu .nav-link.indent {
  padding-left: 40px;
}

.menu .nav-link .pi {
  margin-right: 4px;
  color: #999;
}

.menu .nav-link.active {
  font-weight: 600;
  background-color: #e6e6e6;
}

.menu .submenu {
  transition: all 0.2s ease;
  max-height: 4rem;
}

.menu .submenu.hidden {
  max-height: 0px;
  opacity: 0;
  overflow: hidden;
}

.noselect {
  -webkit-touch-callout: none; /* iOS Safari */
  -webkit-user-select: none; /* Safari */
  -khtml-user-select: none; /* Konqueror HTML */
  -moz-user-select: none; /* Old versions of Firefox */
  -ms-user-select: none; /* Internet Explorer/Edge */
  user-select: none; /* Non-prefixed version, currently supported by Chrome, Edge, Opera and Firefox */
}
</style>