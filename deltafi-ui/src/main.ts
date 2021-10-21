import { createApp } from 'vue'
// const { createApp } = require('vue');
import App from './App.vue'
import './registerServiceWorker'
import router from './router'
import store from './store'
import PrimeVue from 'primevue/config';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import Calendar from 'primevue/calendar';
import ConfirmPopup from 'primevue/confirmpopup';
import ConfirmationService from 'primevue/confirmationservice';
import 'primevue/resources/primevue.min.css';
import 'primevue/resources/themes/bootstrap4-light-blue/theme.css'
import 'primeicons/primeicons.css';
const app = createApp(App, {
  //setup()...
}).use(store).use(router).use(PrimeVue).use(ConfirmationService).component('Column',Column).component('DataTable',DataTable).component('Calendar',Calendar).component('ConfirmPopup',ConfirmPopup).mount('#app')
