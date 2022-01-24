import { ActionTree, ActionContext } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';
import { Mutations } from './mutations';
import { ErrorsMutationTypes } from './mutation-types';
import { ErrorsActionTypes } from './action-types';
import GraphQLService from "@/service/GraphQLService";

type AugmentedActionContext = {
  commit<K extends keyof Mutations>(
    key: K,
    payload: Parameters<Mutations[K]>[1],
  ): ReturnType<Mutations[K]>;
} & Omit<ActionContext<State, RootState>, 'commit'>

export interface Actions {
  [ErrorsActionTypes.FETCH_ERROR_COUNT](
    { commit }: AugmentedActionContext
  ): void;
}

export const actions: ActionTree<State, RootState> & Actions = {
  [ErrorsActionTypes.FETCH_ERROR_COUNT]({ commit }) {
    const graphQLService = new GraphQLService();
    graphQLService.getErrorCount().then(response => {
      commit(ErrorsMutationTypes.SET_ERROR_COUNT, response.data.deltaFiles.totalCount)
    })
  },
};