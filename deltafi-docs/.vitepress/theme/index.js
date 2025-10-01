// https://vitepress.dev/guide/custom-theme
import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import VersionSelect from './components/VersionSelect.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      // Old/stable slot name (works broadly)
      'sidebar-nav-before': () => h(VersionSelect),
      // Newer slot name (kept as fallback)
      'sidebar-top': () => h(VersionSelect)
    }),
  enhanceApp({ app }) {
    app.component('VersionSelect', VersionSelect)
  }
}
