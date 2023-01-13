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
      // deltaFileLinks: [{ name: "View in HTTPBin", url: "https://httpbin.org/anything/example?did=${DID}" }],
      externalLinks: [
        { url: "/graphiql/", name: "GraphiQL", description: "GraphQL query interface" },
        { url: "https://k8s.dev.deltafi.org/#/workloads?namespace=deltafi", name: "Kubernetes Dashboard", description: "Kubernetes admin interface" },
        { url: "https://metrics.dev.deltafi.org/dashboards", name: "Grafana Dashboards", description: "Metrics and logging visualization" },
      ],
    },
  };
};

export default {
  config: generateData(),
};
