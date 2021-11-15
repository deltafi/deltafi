<template>
  <nav class="bg-light menu">
    <div class="pt-3">
      <ul class="nav flex-column">
        <div v-for="item in menuItems" :key="item">
          <li v-if="!item.hidden" class="nav-item">
            <router-link v-if="!item.children" :to="item.path" :class="menuItemClass(item)">
              <i :class="item.icon" />
              {{ item.name }}
            </router-link>
            <span v-else :class="menuItemClass(item)">
              <i :class="item.icon" />
              {{ item.name }}
            </span>
          </li>
          <div v-for="child in item.children" :key="child">
            <li v-if="!child.hidden" class="nav-item">
              <router-link v-if="child.path" :to="child.path" :class="menuItemClass(child, true)">
                <i :class="child.icon" />
                {{ child.name }}
              </router-link>
            </li>
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
          icon: "pi pi-angle-down",
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
          icon: "pi pi-angle-down",
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
          icon: "pi pi-angle-down",
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
              path: "/errors"
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
    menuItemClass(item, isChild = false) {
      let classes = ["nav-link noselect"];
      if (isChild) classes.push("indent");
      if (item.path === this.activePage) classes.push("active");
      if (item.children) classes.push("disabled");
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

.menu .nav-link.disabled {
  font-weight: 300;
  color: #999;
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

.noselect {
  -webkit-touch-callout: none; /* iOS Safari */
  -webkit-user-select: none; /* Safari */
  -khtml-user-select: none; /* Konqueror HTML */
  -moz-user-select: none; /* Old versions of Firefox */
  -ms-user-select: none; /* Internet Explorer/Edge */
  user-select: none; /* Non-prefixed version, currently supported by Chrome, Edge, Opera and Firefox */
}
</style>