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

import _ from "lodash";
import { rest, graphql } from "msw";

export default [
  rest.get("/api/v1/events", (req, res, ctx) => {
    const status = require(`./api/v1/status.ts`);
    const errorCount = 2;

    return res(ctx.set("Connection", "keep-alive"), ctx.set("Content-Type", "text/event-stream"), ctx.status(200, "Mocked status"), ctx.body(`event: errorCount\ndata: ${errorCount}\n\nevent: status\ndata: ${JSON.stringify(status)}\n\n`));
  }),

  rest.get("/api/v1/metrics/graphite", (req, res, ctx) => {
    try {
      const url = new URL(req.url.href);
      const params = new URLSearchParams(url.search);

      if (require.resolve(`.${req.url.pathname}/${params.get("title")}`)) {
        const module = require.resolve(`.${req.url.pathname}/${params.get("title")}`);
        delete require.cache[module];
      }

      const mockModule = require(`.${req.url.pathname}/${params.get("title")}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      return res(ctx.delay(500), ctx.status(200, "Mocked status"), ctx.body(JSON.stringify(responseJson, null, 2)));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  rest.get("/api/v1/content", (req, res, ctx) => {
    try {
      const referenceBase64: string = req.url.searchParams.get("reference") || "";
      const referenceJson = window.atob(referenceBase64);
      const reference = JSON.parse(referenceJson);

      const mockContentModule = require(`.${req.url.pathname}`);
      const responseData = mockContentModule.default(reference);
      return res(ctx.delay(100), ctx.status(200, "Mocked status"), ctx.body(responseData));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  rest.get("/api/v1/*", (req, res, ctx) => {
    try {
      if (require.resolve(`.${req.url.pathname}`)) {
        const module = require.resolve(`.${req.url.pathname}`);
        delete require.cache[module];
      }

      const mockModule = require(`.${req.url.pathname}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      return res(ctx.delay(500), ctx.status(200, "Mocked status"), ctx.body(JSON.stringify(responseJson, null, 2)));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  graphql.query(/.*/, (req, res, ctx) => {
    try {
      if (req.body && "operationName" in req.body) {
        if (require.resolve(`./graphql/${req.body.operationName}`)) {
          const module = require.resolve(`./graphql/${req.body.operationName}`);
          delete require.cache[module];
        }

        const mockModule = require(`./graphql/${req.body.operationName}`);
        const responseJson = "default" in mockModule ? mockModule.default : mockModule;
        return res(ctx.delay(100), ctx.data(responseJson));
      }
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  graphql.mutation(/.*/, (req, res, ctx) => {
    try {
      if (req.body && "operationName" in req.body) {
        if (require.resolve(`./graphql/mutations/${req.body.operationName}`)) {
          const module = require.resolve(`./graphql/mutations/${req.body.operationName}`);
          delete require.cache[module];
        }

        const mockModule = require(`./graphql/mutations/${req.body.operationName}`);
        const responseJson = "default" in mockModule ? mockModule.default : mockModule;
        return res(ctx.delay(100), ctx.data(responseJson));
      }
    } catch (e) {
      console.error(e);
      return;
    }
  }),
];
