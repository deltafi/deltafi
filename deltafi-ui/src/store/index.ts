import { createStore } from 'vuex'
import ApiService from "../service/ApiService";
import GraphQLService from "../service/GraphQLService";

export interface State {
  uiConfig: {
    title: String,
    domain: String
  },
  sidebarHidden: boolean,
  propertySets: Array<Object>,
  loadingPropertySets: boolean
}

export default createStore<State>({
  state: {
    uiConfig: {
      title: 'DeltaFi',
      domain: 'example.deltafi.org'
    },
    sidebarHidden: false,
    propertySets: [],
    loadingPropertySets: false
  },
  mutations: {
    SET_UI_CONFIG(state: State, payload: Object) {
      Object.assign(state.uiConfig, payload)
    },
    TOGGLE_SIDEBAR(state: State) {
      state.sidebarHidden = !state.sidebarHidden
    },
    SET_PROP_SETS(state: State, propertySets: Array<Object>) {
      state.propertySets = propertySets
    },
    SET_LOADING_PROP_SETS(state: State, loading: boolean) {
      state.loadingPropertySets = loading;
    },
  },
  actions: {
    fetchUIConfig(context) {
      const apiService = new ApiService()
      apiService.getConfig().then(response => {
        context.commit('SET_UI_CONFIG', response.config.ui)
      })
    },
    fetchPropertySets(context) {
      const graphQLService = new GraphQLService();
      context.commit('SET_LOADING_PROP_SETS', true)
      graphQLService.getPropertySets().then(response => {
        context.commit('SET_PROP_SETS', response.data.getPropertySets)
        context.commit('SET_LOADING_PROP_SETS', false)
      })
    },
    toggleSidebar(context) {
      context.commit('TOGGLE_SIDEBAR')
    }
  },
  getters: {
    uiConfig: state => {
      return state.uiConfig;
    },
    propertySets: state => {
      return state.propertySets;
    },
    loadingPropertySets: state => {
      return (state.loadingPropertySets && state.propertySets.length === 0);
    },
  },
  modules: {}
})
