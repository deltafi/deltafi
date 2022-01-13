import { ActionTree, ActionContext } from 'vuex';

import { RootState } from '@/store';

import { State } from './state';
import { Mutations } from './mutations';
import { SidebarToggleMutationTypes } from './mutation-types';
import { SidebarToggleActionTypes } from './action-types';

type AugmentedActionContext = {
  commit<K extends keyof Mutations>(
    key: K
  ): ReturnType<Mutations[K]>;
} & Omit<ActionContext<State, RootState>, 'commit'>

export interface Actions {
  [SidebarToggleActionTypes.TOGGLE_SIDEBAR](
    { commit }: AugmentedActionContext,
  ): void;
}

export const actions: ActionTree<State, RootState> & Actions = {
  [SidebarToggleActionTypes.TOGGLE_SIDEBAR]({ commit }) {
    commit(SidebarToggleMutationTypes.TOGGLE_SIDEBAR);
  },
};