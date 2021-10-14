import { createApp } from 'vue'
// const { createApp } = require('vue');
import App from './App.vue'
import './registerServiceWorker'
import router from './router'
import store from './store'

const app = createApp(App, {
  //setup()...
}).use(store).use(router).mount('#app')
