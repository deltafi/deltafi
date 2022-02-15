import { reactive, computed, ComputedRef } from 'vue';
import useApi from './useApi';

export type State = {
  uiConfig: {
    title: String,
    domain: String,
    securityBanner: {
      enabled: Boolean,
      backgroundColor: String,
      textColor: String
      text: String
    }
    dashboard: {
      links: Array<{
        name: String,
        url: String,
        description: String
      }>
    }
  }
}

const state: State = reactive({
  uiConfig: {
    title: 'DeltaFi',
    domain: 'example.deltafi.org',
    securityBanner: {
      enabled: false,
      backgroundColor: "#FFFFFFF",
      textColor: "#000000",
      text: 'DeltaFi',
    },
    dashboard: {
      links: []
    }
  }
})

export default function useUiConfig(): {
  uiConfig: ComputedRef<Object>
  fetchUiConfig: () => void;
} {
  const setUiConfig = ($uiConfig: Object) => {
    Object.assign(state.uiConfig, $uiConfig);
    state.uiConfig.dashboard.links.sort((a, b) => (a.name > b.name) ? 1 : -1);
    return (state.uiConfig);
  };
  const fetchUiConfig = async () => {
    const { response, get } = useApi();
    const endpoint = 'config';
    try {
      await get(endpoint);
      setUiConfig(response.value.config.ui);
    } catch {
      // Continue regardless of error
    }
  };

  return {
    uiConfig: computed(() => state.uiConfig),
    fetchUiConfig,
  };
}