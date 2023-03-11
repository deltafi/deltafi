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

import useGraphQL from './useGraphQL';

export default function useVersion() {
  const { response, queryGraphQL } = useGraphQL();

  const fetchDeltaFileStats = async (inFlightOnly: Boolean = false) => {
    const query = {
      deltaFileStats: {
        __args: {
          inFlightOnly: inFlightOnly,
        },
        count: true,
        referencedBytes: true,
        totalBytes: true
      }
    };

    try {
      await queryGraphQL(query, "deltaFileStats", "query");
      return response.value.data.deltaFileStats;
    } catch {
      // Continue regardless of error
    }
  }

  const fetchAllDeltaFileStats = async () => {
    return await Promise.all([
      fetchDeltaFileStats(false),
      fetchDeltaFileStats(true)
    ]).then((values) => {
      return {
        all: values[0],
        inFlight: values[1],
      }
    });
  }

  return { fetchDeltaFileStats, fetchAllDeltaFileStats };
}