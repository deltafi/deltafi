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

import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

export default function useDeltaFilesQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  const getDeltaFileSearchData = (startDateISOString: String, endDateISOString: String, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, fileName?: string, stageName?: string, actionName?: string, flowName?: string, egressed?: Boolean, filtered?: Boolean, domain?: string, metadata?: Array<Record<string, string>>, ingressBytesMin?: Number, ingressBytesMax?: Number, totalBytesMin?: Number, totalBytesMax?: Number,testMode?: Boolean) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: offSet,
          limit: perPage,
          filter: {
            egressed: egressed,
            filtered: filtered,
            testMode: testMode,
            sourceInfo: {
              flow: flowName,
              filename: fileName
            },
            stage: stageName ? new EnumType(stageName) : null,
            actions: actionName,
            modifiedAfter: startDateISOString,
            modifiedBefore: endDateISOString,
            domains: domain ? [domain] : [],
            indexedMetadata: metadata,
            ingressBytesMin: ingressBytesMin,
            ingressBytesMax: ingressBytesMax,
            totalBytesMin: totalBytesMin,
            totalBytesMax: totalBytesMax
          },
          orderBy: {
            direction: new EnumType(sortDirection),
            field: sortBy
          }
        },
        offset: true,
        count: true,
        totalCount: true,
        deltaFiles: {
          did: true,
          stage: true,
          modified: true,
          created: true,
          sourceInfo: {
            filename: true,
            flow: true,
          },
          totalBytes: true
        }
      }
    };
    return sendGraphQLQuery(query, "getDeltaFileSearchData");
  };

  const getDeltaFilesByDIDs = (didsArray?: string[]) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: 0,
          limit: 10000,
          filter: {
            dids: didsArray
          },
          orderBy: {
            direction: new EnumType('DESC'),
            field: 'modified'
          }
        },
        deltaFiles: {
          did: true,
          stage: true,
          modified: true,
          created: true,
          sourceInfo: {
            filename: true,
            flow: true,
          },
        }
      }
    };
    return sendGraphQLQuery(query, "getDeltaFilesByDIDs");
  };

  const getEnumValuesByEnumType = (enumType: string) => {
    const query = {
      __type: {
        __args: {
          name: enumType
        },
        enumValues: {
          name: true
        }
      }
    };
    return sendGraphQLQuery(query, "getEnumValuesByEnumType");
  };

  const getConfigByType = (typeParam: string) => {
    const query = {
      deltaFiConfigs: {
        __args: {
          configQuery: {
            configType: new EnumType(typeParam)
          }
        },
        name: true
      }
    };
    return sendGraphQLQuery(query, "getConfigByType");
  };

  const sendGraphQLQuery = async (query: any, operationName: string) => {
    try {
      await queryGraphQL(query, operationName);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    getDeltaFileSearchData,
    getDeltaFilesByDIDs,
    getEnumValuesByEnumType,
    getConfigByType
  };
}