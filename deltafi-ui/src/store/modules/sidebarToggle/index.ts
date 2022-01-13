import {
  Store as VuexStore,
  CommitOptions,
  DispatchOptions,
  Module,
} from 'vuex';

import { RootState } from '@/store';

import { state } from './state';
import { mutations, Mutations } from './mutations';
import { actions, Actions } from './actions';

import type { State } from './state';

export { State };

export type SidebarToggleStore<S = State> = Omit<VuexStore<S>, 'commit' | 'dispatch'>
& {
  commit<K extends keyof Mutations>(
    key: K,
    options?: CommitOptions
  ): ReturnType<Mutations[K]>;
} & {
  dispatch<K extends keyof Actions>(
    key: K,
    options?: DispatchOptions
  ): ReturnType<Actions[K]>;
};

export const store: Module<State, RootState> = {
  state,
  mutations,
  actions,
};
