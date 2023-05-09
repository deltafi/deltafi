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

const generateData = () => {
  return {
    ui: {
      domain: "dev.deltafi.org",
      title: "DeltaFi Local",
      useUTC: "false",
      authMode: "basic",
      securityBanner: {
        backgroundColor: "#A020F0",
        enabled: true,
        text: "Mocked Data",
        textColor: "#FFFFFF",
      },
      deltaFileLinks: [
        {
          name: "View in Mock HTTPBin",
          url: "https://mock.httpbin.org/anything/example?did=${did}",
          description: null,
        },
        {
          name: "View Filename in Mock HTTPBin",
          url: "https://mock.httpbin.org/anything/example?filename=${sourceInfo.filename}",
          description: null,
        },
        {
          name: "View test_key in Mock HTTPBin",
          url: "https://mock.httpbin.org/anything/example?filename=${indexedMetadata.test_key}",
          description: null,
        },
      ],
      externalLinks: [
        { url: "/mock-graphiql/", name: "Mock GraphiQL", description: "Mock GraphQL query interface" },
        { url: "https://mock.k8s.dev.deltafi.org/#/workloads?namespace=deltafi", name: "Mock Kubernetes Dashboard", description: "Mock Kubernetes admin interface" },
        { url: "https://mock.metrics.dev.deltafi.org/dashboards", name: "Mock Grafana Dashboards", description: "Mock Metrics and logging visualization" },
      ],
    },
  };
};

export default {
  config: generateData(),
};
