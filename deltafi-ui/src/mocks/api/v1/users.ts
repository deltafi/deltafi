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
  return [
    {
      id: 1,
      name: "Admin",
      dn: "CN=Admin, C=US",
      username: "admin",
      created_at: "2021-2023-11-28T18:27:26.534Z",
      updated_at: "2021-2023-11-28T18:40:05.194Z",
      roles: [
        {
          id: 1,
          name: "Admin",
          permissions: [
            "Admin"
          ],
          created_at: "2021-2023-11-28T18:27:26.243Z",
          updated_at: "2021-2023-11-28T18:27:26.243Z"
        }
      ],
      permissions: [
        "Admin"
      ]
    },
    {
      id: 2,
      name: "John Doe",
      dn: "CN=John Doe",
      username: "johndoe",
      created_at: "2021-2023-11-30T19:57:58.074Z",
      updated_at: "2021-2023-11-30T19:57:58.109Z",
      roles: [
        {
          id: 2,
          name: "Ingress Only",
          permissions: [
            "DeltaFileIngress"
          ],
          created_at: "2021-2023-11-28T18:27:26.318Z",
          updated_at: "2021-2023-11-28T18:27:26.318Z"
        },
        {
          id: 3,
          name: "Read Only",
          permissions: [
            "DashboardView",
            "DeletePolicyRead",
            "DeltaFileContentView",
            "DeltaFileMetadataView",
            "FlowView",
            "IngressRoutingRuleRead",
            "MetricsView",
            "PluginCustomizationConfigView",
            "PluginImageRepoView",
            "PluginsView",
            "SnapshotRead",
            "StatusView",
            "SystemPropertiesRead",
            "UIAccess",
            "VersionsView"
          ],
          created_at: "2021-2023-11-28T18:27:26.435Z",
          updated_at: "2021-2023-11-28T18:27:26.435Z"
        }
      ],
      permissions: [
        "DashboardView",
        "DeletePolicyRead",
        "DeltaFileContentView",
        "DeltaFileIngress",
        "DeltaFileMetadataView",
        "FlowView",
        "IngressRoutingRuleRead",
        "MetricsView",
        "PluginCustomizationConfigView",
        "PluginImageRepoView",
        "PluginsView",
        "SnapshotRead",
        "StatusView",
        "SystemPropertiesRead",
        "UIAccess",
        "VersionsView"
      ]
    }
  ]
};

export default generateData();
