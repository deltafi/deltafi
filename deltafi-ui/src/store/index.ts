import { createStore } from 'vuex'
import ApiService from "../service/ApiService";

export interface State {
  uiConfig: {
    title: String,
    domain: String
  },
  sidebarHidden: boolean
}

export default createStore<State>({
  state: {
    uiConfig: {
      title: 'DeltaFi',
      domain: 'example.deltafi.org'
    },
    sidebarHidden: false
  },
  mutations: {
    SET_UI_CONFIG(state: State, payload: Object) {
      Object.assign(state.uiConfig, payload)
    },
    TOGGLE_SIDEBAR(state: State) {
      state.sidebarHidden = !state.sidebarHidden
    }
  },
  actions: {
    fetchUIConfig(context) {
      const apiService = new ApiService()
      apiService.getConfig().then(response => {
        context.commit('SET_UI_CONFIG', response.config.ui)
      })
    },
    toggleSidebar(context) {
      context.commit('TOGGLE_SIDEBAR')
    }
  },
  getters: {
    uiConfig: state => {
      return state.uiConfig;
    }
  },
  modules: {}
})
