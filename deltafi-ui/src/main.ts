import { createApp } from 'vue'
import App from './App.vue'

// import './registerServiceWorker'
import router from './router'
import store from './store'

import PrimeVue from 'primevue/config';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import Calendar from 'primevue/calendar';
import ConfirmPopup from 'primevue/confirmpopup';
import ConfirmationService from 'primevue/confirmationservice';

import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import 'primevue/resources/themes/bootstrap4-light-blue/theme.css'
import 'primevue/resources/primevue.min.css';
import 'primeicons/primeicons.css';

const app = createApp(App)

app.use(store)
app.use(router)
app.use(PrimeVue)
app.use(ConfirmationService)

app.component('Column', Column)
app.component('DataTable', DataTable)
app.component('Calendar',Calendar)
app.component('ConfirmPopup', ConfirmPopup)

app.mount('#app')
