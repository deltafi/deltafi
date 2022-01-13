import { ActionTree, ActionContext } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';
import { Mutations } from './mutations';
import { SearchOptionsMutationTypes } from './mutation-types';
import { SearchOptionsActionTypes } from './action-types';

type AugmentedActionContext = {
  commit<K extends keyof Mutations>(
    key: K,
    payload: Parameters<Mutations[K]>[1],
  ): ReturnType<Mutations[K]>;
} & Omit<ActionContext<State, RootState>, 'commit'>

export interface Actions {
  [SearchOptionsActionTypes.UPDATE_SEARCH_OPTIONS](
    { commit }: AugmentedActionContext,
    payload: Object
  ): void;
}

export const actions: ActionTree<State, RootState> & Actions = {
  [SearchOptionsActionTypes.UPDATE_SEARCH_OPTIONS]({ commit }, payload: Object) {
    commit(SearchOptionsMutationTypes.SET_SEARCH_OPTIONS, payload)
  },
};