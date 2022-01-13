import { GetterTree } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';

export type Getters = {
  propertySets(state: State): Array<Object>;
  loadingPropertySets(state: State): boolean; 
}

export const getters: GetterTree<State, RootState> & Getters = {
  propertySets: (state) => {
    return state.propertySets;
  },
  loadingPropertySets: (state) => {
    return (state.loadingPropertySets && state.propertySets.length === 0);
  }
};