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

import { rest } from 'msw'


export default [

  rest.get("/api/v1/config", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching UI configs.',
      })
    )
  }),
  rest.get("/api/v1/metrics/system/nodes", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching System Metrics.',
      })
    )
  }),

  rest.get("/api/v1/metrics/action", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Action Metrics.',
      })
    )
  }),

  rest.get("/api/v1/metrics/queues", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Queue Metrics.',
      })
    )
  }),

  rest.get("/api/v1/versions", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Versions.',
      })
    )
  }),

  rest.post("/graphql", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        message: 'Error fetching data from GraphQL',
      })
    )
  }),
]
