import { createStore } from 'vuex'
import ApiService from "../service/ApiService";

export interface State {
  uiConfig: {
    title: String,
    domain: String
  }
}

export default createStore<State>({
  state: {
    uiConfig: {
      title: 'DeltaFi',
      domain: 'example.deltafi.org'
    }
  },
  mutations: {
    SET_UI_CONFIG(state: State, payload: Object) {
      Object.assign(state.uiConfig, payload)
    }
  },
  actions: {
    fetchUIConfig(context) {
      const apiService = new ApiService()
      apiService.getConfig().then(response => {
        context.commit('SET_UI_CONFIG', response.config.ui)
      })
    }
  },
  getters: {
    uiConfig: state => {
      return state.uiConfig;
    }
  },
  modules: {}
})
