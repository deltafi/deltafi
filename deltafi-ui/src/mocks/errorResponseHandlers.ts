/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

import { http, HttpResponse, delay } from 'msw'


export default [

  http.get("/api/v1/config", async () => {
    await delay(1500);
    return HttpResponse.json({
      error: 'Error fetching UI configs.',
    }, { status: 500 })
  }),
  http.get("/api/v1/metrics/system/nodes", async () => {
    await delay(1500);
    return HttpResponse.json({
      error: 'Error fetching System Metrics.',
    }, { status: 500 })
  }),

  http.get("/api/v1/metrics/queues", async () => {
    await delay(1500);
    return HttpResponse.json({
      error: 'Error fetching Queue Metrics.',
    }, { status: 500 })
  }),

  http.get("/api/v1/versions", async () => {
    await delay(1500);
    return HttpResponse.json({
      error: 'Error fetching Versions.',
    }, { status: 500 })
  }),

  http.post("/graphql", async () => {
    await delay(1500);
    return HttpResponse.json({
      message: 'Error fetching data from GraphQL',
    }, { status: 500 })
  }),
]
