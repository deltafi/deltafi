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

import { ref, Ref } from "vue";
import { jsonToGraphQLQuery, EnumType } from 'json-to-graphql-query';
import useNotifications from "./useNotifications";

export default function useGraphQL() {
  const notify = useNotifications();
  const basePath: RequestInfo = "/graphql-core-domain";
  const response: Ref<any> = ref({});
  const loading: Ref<Boolean> = ref(false);
  const loaded: Ref<Boolean> = ref(false);
  const errors: Ref<Array<string>> = ref([]);

  const removeEmptyKeyValues = (queryObj: any): any => {
    const newObj: any = {};
    Object.entries(queryObj).forEach(([k, v]) => {
      if (v instanceof Array) {
        newObj[k] = queryObj[k];
      } else if (v instanceof EnumType) {
        newObj[k] = queryObj[k];
      } else if (v === Object(v)) {
        newObj[k] = removeEmptyKeyValues(v);
      } else if (v != null) {
        newObj[k] = queryObj[k];
      }
    });
    return newObj;
  }

  const clearEmptyObjects = (queryObj: any) => {
    for (const objKey in queryObj) {
      if (!queryObj[objKey] || typeof queryObj[objKey] !== "object") {
        continue;
      }

      clearEmptyObjects(queryObj[objKey]);
      if (Object.keys(queryObj[objKey]).length === 0) {
        delete queryObj[objKey];
      }
    }
  };

  const queryGraphQL = async (query: string | object, queryName: string, queryType: string = "query", bypass: boolean = false) => {
    if (typeof query === 'object' && !bypass ) {
      const cleanedQuery = removeEmptyKeyValues(query);
      clearEmptyObjects(cleanedQuery);
      query = jsonToGraphQLQuery(cleanedQuery, { pretty: false });
    } else if (bypass) {
      query = jsonToGraphQLQuery(query, { pretty: false });
    }
    query = JSON.stringify({ "query": `${queryType} ${queryName} { ${query} }`, "operationName": queryName });
    loading.value = true;
    errors.value = [];
    try {
      const res = await fetch(basePath, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: query
      })
      if (!res.ok) {
        if (res.status == 400) {
          const body = await res.json();
          for (const error of body.errors) {
            notify.error("Error Received from GraphQL", error.message);
            errors.value.push(error.message)
          }
        } else {
          throw Error(res.statusText);
        }
        return Promise.reject(res);
      }
      response.value = await res.json();
      if ('errors' in response.value) {
        for (const error of response.value.errors) {
          notify.error("Error Received from GraphQL", error.message);
          errors.value.push(error.message)
        }
        return Promise.reject(response);
      }
      loaded.value = true;
      return Promise.resolve(res);
    } catch (error: any) {
      errors.value.push(error)
      notify.error("Error Contacting GraphQL", error);
      return Promise.reject(error);
    } finally {
      loading.value = false;
    }
  }

  return { response, loading, loaded, queryGraphQL, errors };
}