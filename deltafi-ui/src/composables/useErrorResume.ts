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

import { EnumType } from "json-to-graphql-query";
import useGraphQL from "./useGraphQL";

export default function useErrorResume() {
  const { response, queryGraphQL } = useGraphQL();

  const resume = async (dids: Array<string>, metadata: Array<Object>) => {
    const query = {
      resume: {
        __args: {
          dids: dids,
          resumeMetadata: metadata,
        },
        did: true,
        success: true,
        error: true,
      },
    };
    return sendGraphQLQuery(query, "errorsResume", "mutation");
  };

  const resumeByErrorCause = async (errorCause: string, metadata: Array<Object> = [], includeAcknowledged: boolean = false, limit: number = 1000) => {
    const query = {
      resumeByErrorCause: {
        __args: {
          errorCause: errorCause,
          resumeMetadata: metadata,
          includeAcknowledged: includeAcknowledged,
          limit: limit,
        },
        did: true,
        success: true,
        error: true,
      },
    };
    return sendGraphQLQuery(query, "resumeByErrorCause", "mutation");
  };

  const resumeByFlow = async (flowType: string, name: string, metadata: Array<Object> = [], includeAcknowledged: boolean = false, limit: number = 1000) => {
    const query = {
      resumeByFlow: {
        __args: {
          flowType: new EnumType(flowType),
          name: name,
          resumeMetadata: metadata,
          includeAcknowledged: includeAcknowledged,
          limit: limit,
        },
        did: true,
        success: true,
        error: true,
      },
    };
    return sendGraphQLQuery(query, "resumeByFlow", "mutation");
  };

  interface queryParams {
    dids?: Array<object>;
    dataSources?: Array<string>;
    dataSinks?: Array<string>;
    transforms?: Array<string>;
  }
  const resumeMatching = async (queryParams: queryParams, metadata: Array<Object> = []) => {
    const query = {
      resumeMatching: {
        __args: {
          filter: {
            ...queryParams,
          },
          resumeMetadata: metadata,
        },
        did: true,
        success: true,
        error: true,
      },
    };

    return sendGraphQLQuery(query, "resumeMatching", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch (e: any) {
      return e.value;
      // Continue regardless of error
    }
  };

  return {
    resume,
    resumeByErrorCause,
    resumeByFlow,
    resumeMatching,
  };
}
