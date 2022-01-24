import { MutationTree } from 'vuex';

import { State } from './state';
import { ErrorsMutationTypes } from './mutation-types';

export type Mutations<S = State> = {
  [ErrorsMutationTypes.SET_ERROR_COUNT](state: State, count: number): void;
}

export const mutations: MutationTree<State> & Mutations = {
  [ErrorsMutationTypes.SET_ERROR_COUNT](state: State, count: number) {
    state.count = count;
  },
};