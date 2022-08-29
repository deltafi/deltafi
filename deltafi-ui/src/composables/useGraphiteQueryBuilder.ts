/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
import _ from "lodash";

export default function useGraphiteQueryBuilder() {
  const { data, fetch } = useGraphiteApi();

  const buildByteRateQuery = (byteType: string, operationName: string) => {
    const queryString = `sortByName(aliasByTags(groupByTags(nonNegativeDerivative(seriesByTag('name=${byteType}', 'metricattribute=count')), 'averageSeries', 'ingressFlow'), 'ingressFlow'), true, false)`;
    const queryEncoded = `target=` + encodeURIComponent(queryString) + `&from=-30s&until=now&format=json&title=${operationName}`;
    return queryEncoded;
  };

  const fetchIngressFlowsByteRate = async () => {
    await fetch(buildByteRateQuery("bytes_in", "fetchIngressFlowsByteRate"));
  };

  const fetchEgressFlowsByteRate = async () => {
    await fetch(buildByteRateQuery("bytes_out", "fetchEgressFlowsByteRate"));
  };

  return { data, fetchIngressFlowsByteRate, fetchEgressFlowsByteRate };
}
