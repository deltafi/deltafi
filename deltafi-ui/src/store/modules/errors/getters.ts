import { GetterTree } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';

export type Getters = {
  count(state: State): number;
}

export const getters: GetterTree<State, RootState> & Getters = {
  count: (state) => {
    return state.count;
  },
};