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

/* eslint vue/multi-word-component-names: 0 */

import { createApp } from 'vue';
import App from '@/App.vue';
import router from '@/router';

import PrimeVue from 'primevue/config';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip';
import ToastService from 'primevue/toastservice';
import BadgeDirective from 'primevue/badgedirective';
import PageHeader from "@/components/PageHeader.vue";
import useUiConfig from "@/composables/useUiConfig";

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import 'primevue/resources/themes/bootstrap4-light-blue/theme.css';
import 'primevue/resources/primevue.min.css';
import 'primeicons/primeicons.css';
import "@fortawesome/fontawesome-free/css/all.css";
import "@/styles/icomoon.scss";
import "@/styles/global.scss";

if (process.env.NODE_ENV === 'development') {
  const responseType = process.env.VUE_APP_MOCK_RESPONSES ? process.env.VUE_APP_MOCK_RESPONSES : "";
  if (["successResponse", "errorResponse", "customResponse"].includes(responseType)) {
    const { worker } = require('./mocks/browser.ts')
    worker.start()
  };
}

const { fetchUiConfig } = useUiConfig();

Promise.all([
  fetchUiConfig()
]).then(() => {
  const app = createApp(App)
  app.use(router)
  app.use(PrimeVue)
  app.use(ConfirmationService)
  app.use(ToastService)
  app.directive('badge', BadgeDirective);
  app.directive('tooltip', Tooltip);
  app.component('PageHeader', PageHeader)
  app.mount('#app')
});