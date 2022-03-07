import { reactive, computed, ComputedRef } from 'vue';
import useApi from './useApi';

export type State = {
  uiConfig: {
    title: String,
    domain: String,
    securityBanner: {
      enabled: Boolean,
      backgroundColor?: String,
      textColor?: String
      text?: String
    }
    externalLinks: Array<{
      name: String,
      url: String,
      description: String
    }>
    deltaFileLinks: Array<{
      name: String,
      url: String
    }>
    useUTC: Boolean
  }
}

const state: State = reactive({
  uiConfig: {
    title: 'DeltaFi',
    domain: 'example.deltafi.org',
    securityBanner: {
      enabled: false,
    },
    externalLinks: [],
    deltaFileLinks: [],
    useUTC: false
  }
})

export default function useUiConfig(): {
  uiConfig: ComputedRef<Record<string, any>>
  fetchUiConfig: () => void
  setUiConfig: (uiConfig: Object) => Object;
} {
  const setUiConfig = ($uiConfig: Record<string, any>) => {
    Object.assign(state.uiConfig, $uiConfig);
    state.uiConfig.externalLinks.sort((a, b) => (a.name > b.name) ? 1 : -1);
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
    setUiConfig,
  };
}