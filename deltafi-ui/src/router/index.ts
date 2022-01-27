import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Dashboard from '@/pages/DashboardPage.vue';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: '',
    component: Dashboard,
  },
  {
    path: '/metrics/system',
    name: 'System Metrics',
    component: () => import('@/pages/SystemMetricsPage.vue')
  },
  {
    path: '/deltafile/search',
    name: 'DeltaFile Search',
    component: () => import('@/pages/DeltaFileSearchPage.vue'),
  },
  {
    path: '/deltafile/upload',
    name: 'DeltaFile Upload',
    component: () => import('@/pages/DeltaFileUploadPage.vue'),
  },
  {
    path: '/metrics/action',
    name: 'Action Metrics',
    component: () => import('@/pages/ActionMetricsPage.vue')
  },
  {
    path: '/metrics/queue',
    name: 'Queue Metrics',
    component: () => import('@/pages/QueueMetricsPage.vue')
  },
  {
    path: '/errors',
    name: 'Errors',
    component: () => import('@/pages/ErrorsPage.vue'),
  },
  {
    path: '/deltafile/viewer/:did?',
    name: 'DeltaFile Viewer',
    component: () => import('@/pages/DeltaFileViewerPage.vue'),
  },
  {
    path: "/config/system",
    name: "System Properties",
    component: () => import('@/pages/SystemPropertiesPage.vue'),
  },
  {
    path: "/config/flow",
    name: "Flow Configuration",
    component: () => import('@/pages/FlowConfigurationPage.vue'),
  },
  {
    path: "/versions",
    name: "Versions",
    component: () => import('@/pages/VersionsPage.vue'),
  },
  {
    path: "/:catchAll(.*)",
    name: "404 Not Found",
    component: () => import('@/pages/PageNotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

export default router