import { createStore, createLogger } from 'vuex';
import createPersistedState from 'vuex-persistedstate';

import { store as sidebarToggle, SidebarToggleStore, State as SidebarToggleState } from '@/store/modules/sidebarToggle';
import { store as propertySets, PropertySetsStore, State as PropertySetsState } from '@/store/modules/propertySets';
import { store as uiConfig, UIConfigStore, State as UIConfigState } from '@/store/modules/uiConfig';
import { store as searchOptions, SearchOptionsStore, State as SearchOptionsState } from '@/store/modules/searchOptions';
import { store as Errors, ErrorsStore, State as ErrorsState } from '@/store/modules/errors';

export type RootState = {
  sidebarToggle: SidebarToggleState;
  propertySets: PropertySetsState;
  uiConfig: UIConfigState;
  searchOptions: SearchOptionsState;
};

export type Store = SidebarToggleStore<Pick<RootState, 'sidebarToggle'>>
& PropertySetsStore<Pick<RootState, 'propertySets'>>
& UIConfigStore<Pick<RootState, 'uiConfig'>>
& SearchOptionsStore<Pick<RootState, 'searchOptions'>>;

// Plug in logger when in development environment
console.log(process.env.NODE_ENV);
const debug = process.env.NODE_ENV !== 'production';
const plugins = debug ? [createLogger({})] : [];

// Plug in session storage based persistence
plugins.push(createPersistedState({ storage: window.sessionStorage }));

export const store = createStore({
  plugins,
  modules: {
    sidebarToggle,
    propertySets,
    uiConfig,
    searchOptions,
    Errors
  },
});

export function useStore(): Store {
  return store as Store;
}