import { MutationTree } from 'vuex';

import { State } from './state';
import { UIConfigMutationTypes } from './mutation-types';

// Mutation Types
export type Mutations<S = State> = {
  [UIConfigMutationTypes.SET_UI_CONFIG](state: S, payload: Object): void;
}

// Define Mutation
export const mutations: MutationTree<State> & Mutations = {
  [UIConfigMutationTypes.SET_UI_CONFIG](state: State, payload: Object) {
    Object.assign(state.uiConfig, payload)
    state.uiConfig.dashboard.links.sort((a, b) => (a.name > b.name) ? 1 : -1)
  },
};