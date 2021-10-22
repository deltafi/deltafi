import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Dashboard from '../views/Dashboard.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: {
      title: 'DeltaFi',
      menuIconClass: 'pi pi-desktop'
    }
  },
  {
    path: '/metrics',
    name: 'Metrics',
    component: () => import('../views/Metrics.vue'),
    meta: {
      title: 'Metrics - DeltaFi',
      menuIconClass: 'pi pi-chart-bar'
    }
  },
  {
    path: '/errors',
    name: 'Errors',
    component: () => import('../views/Errors.vue'),
    meta: {
      title: 'Errors - DeltaFi',
      menuIconClass: 'pi pi-times-circle'
    }
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

export default router
