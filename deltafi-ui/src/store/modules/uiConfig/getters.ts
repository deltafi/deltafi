import { GetterTree } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';

export type Getters = {
  uiConfig(state: State): Object;
  externalLinks(state: State): Object;
}

export const getters: GetterTree<State, RootState> & Getters = {
  uiConfig: (state) => {
    return state.uiConfig;
  },
  externalLinks: (state) => {
    return state.uiConfig.dashboard.links;
  }
}