/* eslint vue/multi-word-component-names: 0 */

import { createApp } from 'vue'
import App from './App.vue'

// import './registerServiceWorker'
import router from './router'
import store from './store'

import PrimeVue from 'primevue/config';
import Button from 'primevue/button';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import Dialog from 'primevue/dialog';
import Calendar from 'primevue/calendar';
import ConfirmPopup from 'primevue/confirmpopup';
import ConfirmationService from 'primevue/confirmationservice';
import Message from 'primevue/message';
import Tag from 'primevue/tag';
import Tooltip from 'primevue/tooltip';
import InlineMessage from 'primevue/inlinemessage';
import ToastService from 'primevue/toastservice';
import Toast from 'primevue/toast';

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
app.use(ToastService)

app.component('Toast',Toast)
app.component('Column', Column)
app.component('DataTable', DataTable)
app.component('Dialog', Dialog)
app.component('Calendar',Calendar)
app.component('ConfirmPopup', ConfirmPopup)
app.component('Button', Button)
app.component('Tag', Tag)
app.component('Message', Message)
app.component('InlineMessage', InlineMessage)

app.directive('tooltip', Tooltip);

app.mount('#app')
