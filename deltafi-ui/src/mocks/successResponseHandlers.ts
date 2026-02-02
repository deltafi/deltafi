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

import { http, graphql, HttpResponse, delay } from "msw";

const requestDelay = parseInt(import.meta.env.VITE_MOCK_REQUEST_DELAY || "500");
console.log("Mock Request Delay: ", requestDelay);

export default [
  http.get("/api/v:version/sse", async () => {
    const status = await import(`./api/v2/status.ts`);
    const errorCount = 2;

    return new HttpResponse(`event: errorCount\ndata: ${errorCount}\n\nevent: status\ndata: ${JSON.stringify(status)}\n\n`, {
      headers: {
        "Connection": "keep-alive",
        "Content-Type": "text/event-stream",
      },
    });
  }),

  http.get("/api/v:version/metrics/graphite", async ({ request }) => {
    try {
      const url = new URL(request.url);
      const params = new URLSearchParams(url.search);

      const mockModule = await import(/* @vite-ignore */ `.${url.pathname}/${params.get("title")}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      await delay(requestDelay);
      return new HttpResponse(JSON.stringify(responseJson, null, 2));
    } catch (e) {
      console.error(e);
      return HttpResponse.json({});
    }
  }),

  http.get("/api/v:version/content", async ({ request }) => {
    try {
      const url = new URL(request.url);
      const contentBase64: string = url.searchParams.get("content") || "";
      const contentJson = window.atob(contentBase64);
      const content = JSON.parse(contentJson);

      const mockContentModule = await import(/* @vite-ignore */ `.${url.pathname}`);
      const responseData = mockContentModule.default(content);
      await delay(requestDelay);
      return new HttpResponse(responseData);
    } catch (e) {
      console.error(e);
      return HttpResponse.json({});
    }
  }),

  http.get("/api/v:version/:rest+", async ({ request }) => {
    try {
      const url = new URL(request.url);
      const mockModule = await import(/* @vite-ignore */ `.${url.pathname}`);
      const responseJson = "default" in mockModule ? mockModule.default : mockModule;
      await delay(requestDelay);
      return new HttpResponse(JSON.stringify(responseJson, null, 2));
    } catch (e) {
      console.error(e);
      return HttpResponse.json({});
    }
  }),

  graphql.query(/.*/, async ({ request }) => {
    try {
      const body = await request.clone().json();
      if (body && "operationName" in body) {
        const mockModule = await import(/* @vite-ignore */ `./graphql/${body.operationName}`);
        let responseJson;
        if ("default" in mockModule) {
          if (typeof mockModule.default === "function") {
            responseJson = mockModule.default(request);
          } else {
            responseJson = mockModule.default;
          }
        } else {
          responseJson = mockModule;
        }
        await delay(requestDelay);
        return HttpResponse.json({ data: responseJson });
      }
    } catch (e) {
      console.error(e);
      return HttpResponse.json({ data: {} });
    }
  }),

  graphql.mutation(/.*/, async ({ request }) => {
    try {
      const body = await request.clone().json();
      if (body && "operationName" in body) {
        const mockModule = await import(/* @vite-ignore */ `./graphql/mutations/${body.operationName}`);
        let responseJson;
        if ("default" in mockModule) {
          if (typeof mockModule.default === "function") {
            responseJson = mockModule.default(request);
          } else {
            responseJson = mockModule.default;
          }
        } else {
          responseJson = mockModule;
        }
        await delay(requestDelay);
        return HttpResponse.json({ data: responseJson });
      }
    } catch (e) {
      console.error(e);
      return HttpResponse.json({ data: {} });
    }
  }),
];
