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

import useGraphQL from "./useGraphQL";

export default function useExternalLinks() {
  const { response, queryGraphQL } = useGraphQL();

  // Save a DeltaFile Link
  const saveDeltaFileLink = (link: string) => {
    const query = {
      saveDeltaFileLink: {
        __args: {
          link: link,
        },
      },
    };
    return sendGraphQLQuery(query, "saveDeltaFileLink", "mutation");
  };

  // Save an External Link
  const saveExternalLink = (link: string) => {
    const query = {
      saveExternalLink: {
        __args: {
          link: link,
        },
      },
    };
    return sendGraphQLQuery(query, "saveExternalLink", "mutation");
  };

  // Remove a DeltaFile Link
  const removeDeltaFileLink = (linkName: string) => {
    const query = {
      removeDeltaFileLink: {
        __args: {
          linkName: linkName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeDeltaFileLink", "mutation");
  };

  // Remove an External Link
  const removeExternalLink = (linkName: string) => {
    const query = {
      removeExternalLink: {
        __args: {
          linkName: linkName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeExternalLink", "mutation");
  };

  // Replace a DeltaFile Link
  const replaceDeltaFileLink = (linkName: string, link: Object) => {
    const query = {
      replaceDeltaFileLink: {
        __args: {
          linkName: linkName,
          link: link,
        },
      },
    };
    return sendGraphQLQuery(query, "replaceDeltaFileLink", "mutation");
  };

  // Replace an External Link
  const replaceExternalLink = (linkName: string, link: Object) => {
    const query = {
      replaceExternalLink: {
        __args: {
          linkName: linkName,
          link: link,
        },
      },
    };
    return sendGraphQLQuery(query, "replaceExternalLink", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    saveDeltaFileLink,
    saveExternalLink,
    removeDeltaFileLink,
    removeExternalLink,
    replaceDeltaFileLink,
    replaceExternalLink,
  };
}
