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
import _ from "lodash";

export default function useDeltaFilesQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  interface queryParams {
    // Paginator query options
    offset?: number;
    perPage?: number;
    sortDirection?: string;
    sortField?: string;
    // Advanced Options query options
    fileName?: string | null;
    pendingAnnotations?: string | null;
    validatedAnnotations?: Array<object>;
    annotations?: Array<object>;
    dataSources?: Array<string>;
    dataSinks?: Array<string>;
    transforms?: Array<string>;
    topics?: Array<string>;
    filteredCause?: string | null;
    requeueMin?: string | null;
    stage?: string | null;
    egressed?: string | null;
    filtered?: string | null;
    testMode?: string | null;
    terminalStage?: string | null;
    replayable?: string | null;
    paused?: string | null;
    pinned?: string | null;
    sizeMin?: string | null;
    sizeMax?: string | null;
    sizeType?: object;
    sizeUnit?: object;
    ingressBytesMin?: string | null;
    ingressBytesMax?: string | null;
    totalBytesMin?: string | null;
    totalBytesMax?: string | null;
    queryAnnotations?: Array<object>;
    queryDateTypeOptions?: string | null;
  }

  const getDeltaFileSearchData = (startDateISOString: String, endDateISOString: String, queryParams: queryParams) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: queryParams.offset,
          limit: queryParams.perPage,
          filter: {
            nameFilter: queryParams.fileName
              ? {
                  name: queryParams.fileName,
                }
              : null,
            egressed: queryParams.egressed,
            filtered: queryParams.filtered,
            testMode: queryParams.testMode,
            dataSources: queryParams.dataSources,
            dataSinks: queryParams.dataSinks,
            transforms: queryParams.transforms,
            topics: queryParams.topics,
            stage: queryParams.stage ? new EnumType(queryParams.stage) : null,
            modifiedAfter: queryParams.queryDateTypeOptions === "Modified" ? startDateISOString : null,
            modifiedBefore: queryParams.queryDateTypeOptions === "Modified" ? endDateISOString : null,
            createdAfter: queryParams.queryDateTypeOptions === "Created" ? startDateISOString : null,
            createdBefore: queryParams.queryDateTypeOptions === "Created" ? endDateISOString : null,
            annotations: queryParams.annotations,
            ingressBytesMin: queryParams.ingressBytesMin,
            ingressBytesMax: queryParams.ingressBytesMax,
            totalBytesMin: queryParams.totalBytesMin,
            totalBytesMax: queryParams.totalBytesMax,
            requeueCountMin: queryParams.requeueMin,
            filteredCause: queryParams.filteredCause,
            replayable: queryParams.replayable,
            terminalStage: queryParams.terminalStage,
            pendingAnnotations: queryParams.pendingAnnotations,
            paused: queryParams.paused,
            pinned: queryParams.pinned,
          },
          orderBy: {
            direction: queryParams.sortDirection ? new EnumType(queryParams.sortDirection) : null,
            field: queryParams.sortField,
          },
        },
        offset: true,
        count: true,
        totalCount: true,
        deltaFiles: {
          dataSource: true,
          did: true,
          stage: true,
          modified: true,
          created: true,
          totalBytes: true,
          name: true,
          paused: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getDeltaFileSearchData");
  };

  const getDeltaFilesByDIDs = async (didsArray: string[], batchSize: number = 1000) => {
    const chunkedDIDsArray = _.chunk(didsArray, batchSize);
    const results = await Promise.all(
      chunkedDIDsArray.map(async (chunk) => {
        return _getDeltaFilesByDIDs(chunk).then((r) => {
          return r.data.deltaFiles.deltaFiles;
        });
      })
    );
    return _.flatten(results);
  };

  const _getDeltaFilesByDIDs = (didsArray?: string[]) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: 0,
          limit: 10000,
          filter: {
            dids: didsArray,
          },
          orderBy: {
            direction: new EnumType("DESC"),
            field: "modified",
          },
        },
        deltaFiles: {
          did: true,
          stage: true,
          modified: true,
          created: true,
          name: true,
          dataSource: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getDeltaFilesByDIDs");
  };

  const getEnumValuesByEnumType = (enumType: string) => {
    const query = {
      __type: {
        __args: {
          name: enumType,
        },
        enumValues: {
          name: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getEnumValuesByEnumType");
  };

  const pendingAnnotations = (did: string) => {
    const query = {
      pendingAnnotations: {
        __args: {
          did: did,
        },
      },
    };
    return sendGraphQLQuery(query, "pendingAnnotations");
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
    pendingAnnotations,
  };
}
