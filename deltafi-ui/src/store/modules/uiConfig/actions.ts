import { ActionTree, ActionContext } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';
import { Mutations } from './mutations';
import { UIConfigMutationTypes } from './mutation-types';
import { UIConfigActionTypes } from './action-types';
import ApiService from "@/service/ApiService";

type AugmentedActionContext = {
  commit<K extends keyof Mutations>(
    key: K,
    payload: Parameters<Mutations[K]>[1],
  ): ReturnType<Mutations[K]>;
} & Omit<ActionContext<State, RootState>, 'commit'>

export interface Actions {
  [UIConfigActionTypes.FETCH_UI_CONFIG](
    { commit }: AugmentedActionContext,
  ): void;
}

export const actions: ActionTree<State, RootState> & Actions = {
  [UIConfigActionTypes.FETCH_UI_CONFIG]({ commit }) {
    const apiService = new ApiService()
    apiService.getConfig().then(response => {
      commit(UIConfigMutationTypes.SET_UI_CONFIG, response.config.ui)
    })
  },
};