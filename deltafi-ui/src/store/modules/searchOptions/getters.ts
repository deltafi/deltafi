import { GetterTree } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';

export type Getters = {
  getStoredSearchOptions(state: State): Object;
}

export const getters: GetterTree<State, RootState> & Getters = {
  getStoredSearchOptions: (state) => {
    return state.searchOptionsState;
  }
};