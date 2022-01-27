
<template>
  <nav class="bg-light menu">
    <div class="pt-3">
      <ul class="nav flex-column">
        <div v-for="(item,i) in menuItems" :key="i">
          <div v-if="!item.hidden">
            <li v-tooltip.right="item.description" class="nav-item">
              <span v-if="item.children" :class="menuItemClass(item)" @click.prevent="item.expand = !item.expand">
                <i :class="folderIcon(item)" />
                {{ item.name }}
              </span>
              <router-link v-else-if="item.path" :to="item.path" :class="menuItemClass(item)">
                <i :class="item.icon" />
                {{ item.name }}
              </router-link>
              <a v-else-if="item.url" :href="item.url" target="_blank" :class="menuItemClass(item)">
                <i :class="item.icon" />
                {{ item.name }}
              </a>
            </li>
            <div v-for="child in item.children" :key="child" :class="{hidden: !item.expand, submenu: true}">
              <li v-if="!child.hidden" v-tooltip.right="child.description" class="nav-item">
                <router-link v-if="child.path" :to="child.path" :class="menuItemClass(child, true)">
                  <span class="d-flex justify-content-between">
                    <span>
                      <i :class="child.icon" />
                      {{ child.name }}
                    </span>
                    <span v-if="child.badge && child.badge().visible" :class="child.badge().class">{{ child.badge().value }}</span>
                  </span>
                </router-link>
                <a v-else-if="child.url" :href="child.url" target="_blank" :class="menuItemClass(child, true)">
                  <i :class="child.icon" />
                  {{ child.name }}
                </a>
              </li>
            </div>
          </div>
        </div>
      </ul>
    </div>
  </nav>
</template>

<script>
import { mapGetters } from "vuex";
import { mapState } from "vuex";
import { useStore } from '@/store';
import { ErrorsActionTypes } from '@/store/modules/errors/action-types';

const refreshInterval = 5000; // 5 seconds

export default {
  name: "AppMenu",
  data() {
    return {
      routes: [],
      activePage: null,
      staticMenuItems: [
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
              icon: "fas fa-chart-line fa-fw",
              path: "/metrics/action",
            },
            {
              name: "Queue Metrics",
              icon: "fas fa-list-alt fa-fw",
              path: "/metrics/queue",
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
              name: "Flow Configuration",
              icon: "fas fa-random fa-fw",
              path: "/config/flow",
              hidden: false,
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
              badge: () => {
                return this.errorsBadge;
              }
            },
            {
              name: "Viewer",
              icon: "far fa-file fa-fw",
              path: "/deltafile/viewer/",
            },
            {
              name: "Upload",
              icon: "fas fa-upload fa-fw",
              path: "/deltafile/upload/",
            },
          ],
        },
        { name: "Versions", icon: "fas fa-info-circle fa-fw", path: "/versions" },
      ],
    };
  },
  computed: {
    menuItems() {
      let items = this.staticMenuItems
      if (this.externalLinks.length > 0) {
        const externalLinksObject = {
          name: "External Links",
          expand: true,
          children: this.externalLinks.map(link => {
            link.icon = "fas fa-external-link-alt fa-fw";
            return link;
          })
        }

        const objIndex = items.findIndex((obj => obj.name == "External Links"));
        if (objIndex != -1) {
          items[objIndex] = externalLinksObject;
        } else {
          items.splice(4, 0, externalLinksObject);
        }
      }
      return items;
    },
    errorsBadge() {
      return {
        visible: (this.errorCount > 0),
        class: 'badge badge-danger badge-pill',
        value: this.errorCount
      }
    },
    ...mapState({
      errorCount: state => state.Errors.count,
    }),
    ...mapGetters(['externalLinks'])
  },
  watch: {
    $route(to) {
      this.activePage = to.path;
    },
  },
  beforeCreate() {
    this.store = useStore();
    this.store.dispatch(ErrorsActionTypes.FETCH_ERROR_COUNT);
    this.autoRefresh = setInterval(
      function () {
        this.store.dispatch(ErrorsActionTypes.FETCH_ERROR_COUNT);
      }.bind(this),
      refreshInterval
    );
  },
  methods: {
    folderIcon(item) {
      return item.expand ? "fas fa-angle-down fa-fw" : "fas fa-angle-right fa-fw";
    },
    menuItemClass(item, isChild = false) {
      let classes = ["nav-link", "noselect"];
      if (isChild) classes.push("indent");
      if (item.path === this.activePage) classes.push("active");
      if (item.children) classes.push("folder");
      return classes.join(" ");
    },
  },
  store: null
};
</script>

<style scoped lang="scss">
  @import "@/styles/components/sidebar/app-menu.scss";
</style>