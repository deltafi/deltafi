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

import { ref } from "vue";
import useGraphQL from "./useGraphQL";
export default function usePlugins() {
  const { errors, loading, loaded, queryGraphQL, response } = useGraphQL();
  const data = ref(null);

  const fetch = async () => {
    const query = {
      plugins: {
        displayName: true,
        description: true,
        actionKitVersion: true,
        imageName: true,
        imageTag: true,
        imagePullSecret: true,
        installState: true,
        installError: true,
        installAttempts: true,
        lastSuccessfulVersion: true,
        canRollback: true,
        disabled: true,
        pluginCoordinates: {
          artifactId: true,
          groupId: true,
          version: true,
        },
        actions: {
          name: true,
          actionOptions: {
            description: true,
          }
        },
        variables: {
          name: true,
          value: true,
          description: true,
          defaultValue: true,
          dataType: true,
        }
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

  const installPlugin = async (image: String, imagePullSecret: String) => {
    const query = {
      installPlugin: {
        __args: {
          image: image,
          imagePullSecret: imagePullSecret,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "installPlugin", "mutation");
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

  const retryPluginInstall = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      retryPluginInstall: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "retryPluginInstall", "mutation");
  };

  const rollbackPlugin = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      rollbackPlugin: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "rollbackPlugin", "mutation");
  };

  const disablePlugin = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      disablePlugin: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "disablePlugin", "mutation");
  };

  const enablePlugin = async (groupId: String, artifactId: String, version: String) => {
    const query = {
      enablePlugin: {
        __args: {
          pluginCoordinates: {
            groupId: groupId,
            artifactId: artifactId,
            version: version,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "enablePlugin", "mutation");
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
    errors,
    data,
    loading,
    loaded,
    response,
    installPlugin,
    uninstallPlugin,
    retryPluginInstall,
    rollbackPlugin,
    disablePlugin,
    enablePlugin,
    fetch,
    setPluginVariableValues,
  };
}
