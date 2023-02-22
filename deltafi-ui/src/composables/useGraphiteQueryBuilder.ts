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

import useGraphiteApi from "./useGraphiteApi";

export default function useGraphiteQueryBuilder() {
  const { data, fetch } = useGraphiteApi();

  const buildByteRateQuery = (byteType: string, operationName: string) => {
    const queryString = `sortByName(aliasByTags(groupByTags(transformNull(scale(scaleToSeconds(seriesByTag('name=${byteType}'), 1), 8), 0), 'sum', 'ingressFlow'), 'ingressFlow'), true, false)`;
    const queryEncoded = `target=` + encodeURIComponent(queryString) + `&from=-40s&until=-10s&format=json&title=${operationName}`;
    return queryEncoded;
  };

  const fetchIngressFlowsByteRate = async () => {
    await fetch(buildByteRateQuery("stats_counts.bytes_in", "fetchIngressFlowsByteRate"));
  };

  const fetchEgressFlowsByteRate = async () => {
    await fetch(buildByteRateQuery("stats_counts.bytes_out", "fetchEgressFlowsByteRate"));
  };

  return { data, fetchIngressFlowsByteRate, fetchEgressFlowsByteRate };
}
