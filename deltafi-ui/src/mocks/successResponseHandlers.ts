/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

import { rest, graphql } from "msw";

const requestDelay = parseInt(import.meta.env.VITE_MOCK_REQUEST_DELAY || "500");
console.log("Mock Request Delay: ", requestDelay);

export default [
  rest.get("/api/v*/sse", async (req, res, ctx) => {
    const status = await import(`./api/v2/status.ts`);
    const errorCount = 2;

    return res(ctx.set("Connection", "keep-alive"), ctx.set("Content-Type", "text/event-stream"), ctx.status(200, "Mocked status"), ctx.body(`event: errorCount\ndata: ${errorCount}\n\nevent: status\ndata: ${JSON.stringify(status)}\n\n`));
  }),

  rest.get("/api/v*/metrics/graphite", async (req, res, ctx) => {
    try {
      const url = new URL(req.url.href);
      const params = new URLSearchParams(url.search);

      const mockModule = await import(/* @vite-ignore */ `.${req.url.pathname}/${params.get("title")}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      return res(ctx.delay(requestDelay), ctx.status(200, "Mocked status"), ctx.body(JSON.stringify(responseJson, null, 2)));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  rest.get("/api/v*/content", async (req, res, ctx) => {
    try {
      const contentBase64: string = req.url.searchParams.get("content") || "";
      const contentJson = window.atob(contentBase64);
      const content = JSON.parse(contentJson);

      const mockContentModule = await import(/* @vite-ignore */ `.${req.url.pathname}`);
      const responseData = mockContentModule.default(content);
      return res(ctx.delay(requestDelay), ctx.status(200, "Mocked status"), ctx.body(responseData));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  rest.get("/api/v*/*", async (req, res, ctx) => {
    try {
      const mockModule = await import(/* @vite-ignore */ `.${req.url.pathname}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      return res(ctx.delay(requestDelay), ctx.status(200, "Mocked status"), ctx.body(JSON.stringify(responseJson, null, 2)));
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  graphql.query(/.*/, async (req, res, ctx) => {
    try {
      if (req.body && "operationName" in req.body) {
        const mockModule = await import(/* @vite-ignore */ `./graphql/${req.body.operationName}`);
        let responseJson;
        if ("default" in mockModule) {
          if (typeof mockModule.default === "function") {
            responseJson = mockModule.default(req);
          } else {
            responseJson = mockModule.default;
          }
        } else {
          responseJson = mockModule;
        }
        return res(ctx.delay(requestDelay), ctx.data(responseJson));
      }
    } catch (e) {
      console.error(e);
      return;
    }
  }),

  graphql.mutation(/.*/, async (req, res, ctx) => {
    try {
      if (req.body && "operationName" in req.body) {
        const mockModule = await import(/* @vite-ignore */ `./graphql/mutations/${req.body.operationName}`);
        const responseJson = "default" in mockModule ? mockModule.default : mockModule;
        return res(ctx.delay(requestDelay), ctx.data(responseJson));
      }
    } catch (e) {
      console.error(e);
      return;
    }
  }),
];
