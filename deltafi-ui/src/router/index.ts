/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

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