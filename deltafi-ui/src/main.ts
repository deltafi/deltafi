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
import 'primevue/resources/primevue.min.css';
import 'primevue/resources/themes/bootstrap4-light-blue/theme.css'
import 'primeicons/primeicons.css';
const app = createApp(App, {
  //setup()...
}).use(store).use(router).use(PrimeVue).component('Column',Column).component('DataTable',DataTable).component('Calendar',Calendar).mount('#app')
