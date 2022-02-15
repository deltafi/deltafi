import { reactive, computed, ComputedRef } from 'vue';

export type State = {
  sidebarHidden: Boolean
}

const state: State = reactive({
  sidebarHidden: false
})

export default function useUiConfig(): {
  sidebarHidden: ComputedRef<Boolean>
  toggleSidebarHidden: () => void;
} {
  const setSidebarHidden = ($sidebarHidden: Boolean) => {
    return (state.sidebarHidden = $sidebarHidden);
  };
  const toggleSidebarHidden = async () => {
    setSidebarHidden(!state.sidebarHidden);
  }

  return {
    sidebarHidden: computed(() => state.sidebarHidden),
    toggleSidebarHidden
  };
}