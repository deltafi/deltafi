import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Dashboard from '../pages/DashboardPage.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: {
      title: 'DeltaFi',
      menuIconClass: 'pi pi-desktop',
      menuOrder: 1
    }
  },
  {
    path: '/metrics/system',
    name: 'System Metrics',
    component: () => import('../pages/SystemMetricsPage.vue'),
    meta: {
      title: 'Metrics - DeltaFi',
      menuIconClass: 'pi pi-chart-bar',
      menuOrder: 2
    }
  },
  {
    path: '/errors',
    name: 'Errors',
    component: () => import('../pages/ErrorsPage.vue'),
    meta: {
      title: 'Errors - DeltaFi',
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
