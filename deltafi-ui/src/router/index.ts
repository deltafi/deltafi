import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Dashboard from '../pages/DashboardPage.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: {
      menuIconClass: 'pi pi-desktop',
      menuOrder: 1
    }
  },
  {
    path: '/metrics/system',
    name: 'System Metrics',
    component: () => import('../pages/SystemMetricsPage.vue'),
    meta: {
      title: 'Metrics',
      menuIconClass: 'pi pi-chart-bar',
      menuOrder: 2
    }
  },
  {
    path: '/errors',
    name: 'Errors',
    component: () => import('../pages/ErrorsPage.vue'),
    meta: {
      title: 'Errors',
      menuIconClass: 'pi pi-times-circle',
      menuOrder: 2
    }
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

export default router