import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Dashboard from '../pages/DashboardPage.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: '',
    component: Dashboard,
  },
  {
    path: '/metrics/system',
    name: 'System Metrics',
    component: () => import('../pages/SystemMetricsPage.vue')
  },
  {
    path: '/errors',
    name: 'Errors',
    component: () => import('../pages/ErrorsPage.vue'),
  },
  {
    path: '/deltafile/viewer/:did?',
    name: 'DeltaFile Viewer',
    component: () => import('../pages/DeltaFileViewerPage.vue'),
  },
  {
    path: "/:catchAll(.*)",
    name: "404 Not Found",
    component: () => import('../pages/PageNotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

export default router