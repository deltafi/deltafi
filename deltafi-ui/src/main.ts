/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
import Lara from "@primeuix/themes/lara";
import { definePreset } from "@primeuix/themes";

// Custom theme preset based on Lara with Bootstrap-like colors
const DeltaFiPreset = definePreset(Lara, {
  semantic: {
    primary: {
      50: "{blue.50}",
      100: "{blue.100}",
      200: "{blue.200}",
      300: "{blue.300}",
      400: "{blue.400}",
      500: "{blue.500}",
      600: "{blue.600}",
      700: "{blue.700}",
      800: "{blue.800}",
      900: "{blue.900}",
      950: "{blue.950}",
    },
    colorScheme: {
      light: {
        surface: {
          0: "#ffffff",
          50: "{slate.50}",
          100: "{slate.100}",
          200: "{slate.200}",
          300: "{slate.300}",
          400: "{slate.400}",
          500: "{slate.500}",
          600: "{slate.600}",
          700: "{slate.700}",
          800: "{slate.800}",
          900: "{slate.900}",
          950: "{slate.950}",
        },
      },
    },
  },
});

import ConfirmationService from "primevue/confirmationservice";
import Tooltip from "primevue/tooltip";
import ToastService from "primevue/toastservice";
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
    const workerStarted = worker.start({
      // turn off MSW warnings for specific routes
      onUnhandledRequest(req: Request, print: { warning: () => void }) {
        // specify routes to exclude
        const excludedRoutes = ["/js/", "/fonts/", "/src/", "/@vite/", "/node_modules/", "/logo.png"];

        // check if the request URL pathname contains excludedRoutes
        const pathname = new URL(req.url).pathname;
        const isExcluded = excludedRoutes.some((route) => pathname.includes(route));

        if (isExcluded) {
          return;
        }

        print.warning();
      },
    });
    const timeout = new Promise((resolve) => setTimeout(resolve, 5000));
    await Promise.race([workerStarted, timeout]);
  }
}

const { fetchCurrentUser } = useCurrentUser();
const { fetchUiConfig } = useUiConfig();
const { fetchAppPermissions } = usePermissions();

Promise.all([fetchCurrentUser(), fetchUiConfig(), fetchAppPermissions()]).then((values) => {
  const app = createApp(App);
  app.use(auth, values[0], router);
  app.use(router);
  app.use(PrimeVue, {
    theme: {
      preset: DeltaFiPreset,
      options: {
        darkModeSelector: false,
        cssLayer: false,
      },
    },
  });
  app.use(ConfirmationService);
  app.use(ToastService);
  app.directive("tooltip", Tooltip);
  app.component("PageHeader", PageHeader);
  app.mount("#app");
});
