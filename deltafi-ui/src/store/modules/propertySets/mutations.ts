import { MutationTree } from 'vuex';

import { State } from './state';
import { PropertySetsMutationTypes } from './mutation-types';

export type Mutations<S = State> = {
  [PropertySetsMutationTypes.SET_PROP_SETS](state: S, propertySets: Array<Object>): void;
  [PropertySetsMutationTypes.SET_LOADING_PROP_SETS](state: S, loading: boolean): void;
}

export const mutations: MutationTree<State> & Mutations = {
  [PropertySetsMutationTypes.SET_LOADING_PROP_SETS](state: State, loading: boolean) {
    state.loadingPropertySets = loading;
  },
  [PropertySetsMutationTypes.SET_PROP_SETS](state: State, propertySets: Array<Object>) {
    state.propertySets = propertySets;
  },
};