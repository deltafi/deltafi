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

import { ref } from "vue";
import useGraphQL from "./useGraphQL";
export default function usePlugins() {
  const { response, queryGraphQL, loading, loaded } = useGraphQL();
  const data = ref(null);

  const fetch = async () => {
    const query = {
      plugins: {
        displayName: true,
        description: true,
        actionKitVersion: true,
        pluginCoordinates: {
          artifactId: true,
          groupId: true,
          version: true,
        },
        actions: {
          name: true,
          description: true,
          requiresDomains: true,
          requiresEnrichments: true,
        },
        variables: {
          name: true,
          value: true,
          description: true,
          defaultValue: true,
          dataType: true,
        },
        propertySets: {
          id: true,
          description: true,
          properties: {
            key: true,
            value: true,
            defaultValue: true,
            description: true,
            editable: true,
            hidden: true,
            refreshable: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "getPlugin");
  };

  const setPluginVariableValues = async (update: Object) => {
    const query = {
      setPluginVariableValues: {
        __args: update,
      },
    };
    return sendGraphQLQuery(query, "updatePluginVariable", "mutation");
  };

  const installPlugin = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      installPlugin: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "installPlugin", "mutation");
  };

  const installPluginWithSettings = async (groupId: String, artifactId: String, version: String, imageRepositoryOverride: String, imagePullSecretOverride: String, customDeploymentYaml: String) => {
    const query = {
      installPluginWithSettings: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
          imageRepositoryOverride: imageRepositoryOverride,
          imagePullSecretOverride: imagePullSecretOverride,
          customDeploymentYaml: customDeploymentYaml,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "installPluginWithSettings", "mutation");
  };

  const uninstallPlugin = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      uninstallPlugin: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "uninstallPlugin", "mutation");
  };

  const verifyActionsAreRegistered = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      verifyActionsAreRegistered: {
        pluginCoordinates: {
          groupId: groupId,
          artifactId: artifactId,
          version: version,
        },
      },
    };
    await queryGraphQL(query, "updatePluginVariable", "mutation");
    return;
  };

  const getPluginImageRepositories = async () => {
    const query = {
      getPluginImageRepositories: {
        imageRepositoryBase: true,
        pluginGroupIds: true,
        imagePullSecret: true,
      },
    };
    return sendGraphQLQuery(query, "getPluginImageRepositories");
  };

  const savePluginImageRepository = async (imageRepoBase: String, pluginGroupIdsList: Array<String>, newImagePullSecret: String | null) => {
    const query = {
      savePluginImageRepository: {
        __args: {
          pluginImageRepository: {
            imageRepositoryBase: imageRepoBase,
            pluginGroupIds: pluginGroupIdsList,
            imagePullSecret: newImagePullSecret,
          },
        },
        imageRepositoryBase: true,
        pluginGroupIds: true,
        imagePullSecret: true,
      },
    };
    return sendGraphQLQuery(query, "savePluginImageRepository", "mutation");
  };

  const removePluginImageRepository = async (pluginImageRepositoryId: String) => {
    const query = {
      removePluginImageRepository: {
        __args: {
          id: pluginImageRepositoryId,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "removePluginImageRepository", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      data.value = response.value.data;
      return response.value.data;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    data,
    loading,
    loaded,
    response,
    installPlugin,
    installPluginWithSettings,
    uninstallPlugin,
    fetch,
    setPluginVariableValues,
    verifyActionsAreRegistered,
    getPluginImageRepositories,
    savePluginImageRepository,
    removePluginImageRepository,
  };
}
