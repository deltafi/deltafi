import { MutationTree } from 'vuex';

import { State } from './state';
import { SidebarToggleMutationTypes } from './mutation-types';

export type Mutations<S = State> = {
  [SidebarToggleMutationTypes.TOGGLE_SIDEBAR](state: S): void;
}

export const mutations: MutationTree<State> & Mutations = {
  [SidebarToggleMutationTypes.TOGGLE_SIDEBAR](state: State) {
    state.sidebarHidden = !state.sidebarHidden
  }
};