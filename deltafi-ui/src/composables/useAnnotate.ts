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

import useNotifications from "./useNotifications";
import useUtilFunctions from './useUtilFunctions';
import useGraphQL from "./useGraphQL";

const maxSuccessDisplay = 20;

export default function useAnnotate() {
  const notify = useNotifications();
  const endpoint: string = '/api/v2/deltafile/annotate';
  const { pluralize } = useUtilFunctions();
  const { response, queryGraphQL } = useGraphQL();

  const post = async (url: string) => {
    try {
      const res = await fetch(url, { method: 'POST' });
      if (!res.ok) return Promise.reject(res);
      return Promise.resolve(res);
    } catch (error: any) {
      return Promise.reject(error);
    }
  }

  const annotate = async (dids: string | Array<string>, metadata: string, allowOverwrites: Boolean) => {
    dids = Array.isArray(dids) ? dids : new Array(dids);
    let updatedMetadata = metadata.replaceAll(': ', '=');
    updatedMetadata = updatedMetadata.replaceAll(', ', '&');
    const promises = dids.map((did) => {
      const url = allowOverwrites ?
        `${endpoint}/${did}/allowOverwrites?${updatedMetadata}` :
        `${endpoint}/${did}?${updatedMetadata}`;
      try {
        return post(url);
      } catch (response: any) {
        return Promise.reject(response);
      }
    });

    await Promise.all(promises);
    const pluralized = pluralize(dids.length, "DeltaFile");
    const links = dids.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did.split('-')[0]}</a>`);
    if (dids.length > maxSuccessDisplay) {
      links.push("...")
    }
    notify.info(`Annotated ${pluralized}`, links.join(", "))
  }

  const getAnnotationKeys = async () => {
    const query = {
      annotationKeys: {
        __args: {},
      },
    };
    await queryGraphQL(query, "getAnnotationKeys", "query", true);
    return response.value.data.annotationKeys;
  };

  return { annotate, getAnnotationKeys };
}