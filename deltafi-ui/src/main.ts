/* eslint vue/multi-word-component-names: 0 */

import { createApp } from 'vue';
import App from '@/App.vue';
import router from '@/router';
import { store } from "@/store";

import PrimeVue from 'primevue/config';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip';
import ToastService from 'primevue/toastservice';
import BadgeDirective from 'primevue/badgedirective';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import 'primevue/resources/themes/bootstrap4-light-blue/theme.css';
import 'primevue/resources/primevue.min.css';
import 'primeicons/primeicons.css';
import "@fortawesome/fontawesome-free/css/all.css";
import "@/styles/global.scss";

const app = createApp(App)
app.use(router)
app.use(store)
app.use(PrimeVue)
app.use(ConfirmationService)
app.use(ToastService)
app.directive('badge', BadgeDirective);

app.directive('tooltip', Tooltip);

app.mount('#app')
