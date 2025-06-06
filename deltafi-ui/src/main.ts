/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

import { createApp } from "vue";
import App from "@/App.vue";
import router from "@/router";

import PrimeVue from "primevue/config";
import ConfirmationService from "primevue/confirmationservice";
import Tooltip from "primevue/tooltip";
import ToastService from "primevue/toastservice";
import BadgeDirective from "primevue/badgedirective";
import PageHeader from "@/components/PageHeader.vue";
import useCurrentUser from "@/composables/useCurrentUser";
import useUiConfig from "@/composables/useUiConfig";
import usePermissions from "@/composables/usePermissions";

import "@/styles/bootstrap.scss";
import "bootstrap/dist/js/bootstrap.bundle.min.js";
import "primeicons/primeicons.css";
import "@fortawesome/fontawesome-free/css/all.css";
import "@/styles/icomoon.scss";
import "@/styles/global.scss";

import auth from "./plugins/auth";

if (import.meta.env.MODE === "development" || import.meta.env.MODE === "test") {
  const responseType = import.meta.env.VITE_MOCK_RESPONSES ? import.meta.env.VITE_MOCK_RESPONSES : "";
  if (["successResponse", "errorResponse", "customResponse"].includes(responseType)) {
    const { worker } = await import("./mocks/browser.ts");
    worker.start({
      // turn off MSW warnings for specific routes
      onUnhandledRequest(req: any, print: any) {
        // specify routes to exclude
        const excludedRoutes = ["/js/", "/fonts/"];

        // check if the req.url.pathname contains excludedRoutes
        const isExcluded = excludedRoutes.some((route) => req.url.pathname.includes(route));

        if (isExcluded) {
          return;
        }

        print.warning();
      },
    });
  }
}

const { fetchCurrentUser } = useCurrentUser();
const { fetchUiConfig } = useUiConfig();
const { fetchAppPermissions } = usePermissions();

Promise.all([fetchCurrentUser(), fetchUiConfig(), fetchAppPermissions()]).then((values) => {
  const app = createApp(App);
  app.use(auth, values[0], router);
  app.use(router);
  app.use(PrimeVue);
  app.use(ConfirmationService);
  app.use(ToastService);
  app.directive("badge", BadgeDirective);
  app.directive("tooltip", Tooltip);
  app.component("PageHeader", PageHeader);
  app.mount("#app");
});
