/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

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
  },
  topBar?: {
    backgroundColor?: String,
    textColor?: String
  },
  externalLinks?: Array<{
    name: String,
    url: String,
    description: String
  }>
  deltaFileLinks?: Array<{
    name: String,
    url: String
  }>
  useUTC?: Boolean,
  authMode?: String
}

const uiConfig: UiConfig = reactive({
  title: 'DeltaFi',
  domain: 'example.deltafi.org',
  securityBanner: {
    enabled: false,
  },
  topBar: {},
  externalLinks: [],
  deltaFileLinks: [],
  useUTC: false,
  authMode: 'disabled',
})

export default function useUiConfig(): {
  uiConfig: DeepReadonly<UiConfig>
  fetchUiConfig: () => Promise<UiConfig>
  setUiConfig: (uiConfig: UiConfig) => Object;
} {
  const setUiConfig = ($uiConfig: UiConfig) => {
    if ($uiConfig.externalLinks) $uiConfig.externalLinks.sort((a, b) => (a.name > b.name) ? 1 : -1);
    $uiConfig.useUTC = (($uiConfig.useUTC || "").toString() == "true")
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
    return uiConfig;
  };

  return {
    uiConfig: readonly(uiConfig),
    fetchUiConfig,
    setUiConfig,
  };
}
