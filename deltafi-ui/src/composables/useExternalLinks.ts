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
  const saveLink = (link: string) => {
    const query = {
      saveLink: {
        __args: {
          link: link,
        },
        id: true,
        name: true,
        description: true,
        url: true,
        linkType: true
      },
    };
    return sendGraphQLQuery(query, "saveLink", "mutation");
  };

  // Remove a DeltaFile Link
  const removeLink = (linkId: string) => {
    const query = {
      removeLink: {
        __args: {
          id: linkId,
        },
      },
    };
    return sendGraphQLQuery(query, "removeLink", "mutation");
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
    saveLink,
    removeLink
  };
}
