import { reactive, readonly, DeepReadonly } from 'vue';
import useApi from './useApi';

export type UiConfig = {
  title?: String,
  domain?: String,
  securityBanner?: {
    enabled: Boolean,
    backgroundColor?: String,
    textColor?: String
    text?: String
  }
  externalLinks?: Array<{
    name: String,
    url: String,
    description: String
  }>
  deltaFileLinks?: Array<{
    name: String,
    url: String
  }>
  useUTC?: Boolean
}

const uiConfig: UiConfig = reactive({
  title: 'DeltaFi',
  domain: 'example.deltafi.org',
  securityBanner: {
    enabled: false,
  },
  externalLinks: [],
  deltaFileLinks: [],
  useUTC: false
})

export default function useUiConfig(): {
  uiConfig: DeepReadonly<UiConfig>
  fetchUiConfig: () => void
  setUiConfig: (uiConfig: UiConfig) => Object;
} {
  const setUiConfig = ($uiConfig: UiConfig) => {
    if ($uiConfig.externalLinks) $uiConfig.externalLinks.sort((a, b) => (a.name > b.name) ? 1 : -1);
    Object.assign(uiConfig, $uiConfig);
    return uiConfig;
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
    uiConfig: readonly(uiConfig),
    fetchUiConfig,
    setUiConfig,
  };
}