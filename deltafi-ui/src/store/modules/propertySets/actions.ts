import { ActionTree, ActionContext } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';
import { Mutations } from './mutations';
import { PropertySetsMutationTypes } from './mutation-types';
import { PropertySetsActionTypes } from './action-types';
import GraphQLService from "@/service/GraphQLService";

type AugmentedActionContext = {
  commit<K extends keyof Mutations>(
    key: K,
    payload: Parameters<Mutations[K]>[1],
  ): ReturnType<Mutations[K]>;
} & Omit<ActionContext<State, RootState>, 'commit'>

export interface Actions {
  [PropertySetsActionTypes.FETCH_PROPERTY_SETS](
    { commit }: AugmentedActionContext
  ): void;
}

export const actions: ActionTree<State, RootState> & Actions = {
  [PropertySetsActionTypes.FETCH_PROPERTY_SETS]({ commit }) {
    const graphQLService = new GraphQLService();
    commit(PropertySetsMutationTypes.SET_LOADING_PROP_SETS, true)
    graphQLService.getPropertySets().then(response => {
      commit(PropertySetsMutationTypes.SET_PROP_SETS, response.data.getPropertySets)
      commit(PropertySetsMutationTypes.SET_LOADING_PROP_SETS, false)
    })
  },
};