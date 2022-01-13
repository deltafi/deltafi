import { MutationTree } from 'vuex';

import { State } from './state';
import {SearchOptionsMutationTypes } from './mutation-types';

export type Mutations<S = State> = {
  [SearchOptionsMutationTypes.SET_SEARCH_OPTIONS](state: S, payload: Object): void;
}

export const mutations: MutationTree<State> & Mutations = {
  [SearchOptionsMutationTypes.SET_SEARCH_OPTIONS](state: State, payload: Object) {
    Object.assign(state.searchOptionsState, payload)
  }
};