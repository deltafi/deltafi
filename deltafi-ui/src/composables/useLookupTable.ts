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

import useGraphQL from "./useGraphQL";
import _ from "lodash";

export default function useLookupTable() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const getLookupTable = (lookupTableName: string) => {
    const query = {
      getLookupTable: {
        __args: {
          lookupTableName: lookupTableName,
        },
        name: true,
        columns: true,
        keyColumns: true,
        serviceBacked: true,
        backingServiceActive: true,
        pullThrough: true,
        refreshDuration: true,
        lastRefresh: true,
        totalRows: true,
      },
    };
    return sendGraphQLQuery(query, "getLookupTable");
  };

  const getLookupTables = () => {
    const query = {
      getLookupTables: {
        name: true,
        columns: true,
        keyColumns: true,
        serviceBacked: true,
        backingServiceActive: true,
        pullThrough: true,
        refreshDuration: true,
        lastRefresh: true,
        totalRows: true,
      },
    };
    return sendGraphQLQuery(query, "getLookupTables");
  };

  const lookup = (lookupTableName: string, matchingColumnValues: any, resultColumns: any, sortColumn: any, sortDirection: any, offset: any, limit: any) => {
    const query = {
      lookup: {
        __args: {
          lookupTableName: lookupTableName,
          matchingColumnValues: matchingColumnValues,
          resultColumns: resultColumns,
          sortColumn: sortColumn,
          sortDirection: sortDirection,
          offset: offset,
          limit: limit,
        },
        offset: true,
        count: true,
        totalCount: true,
        rows: {
          column: true,
          value: true,
        },
      },
    };
    return sendGraphQLQuery(query, "lookup");
  };

  // Create a Lookup Table
  const createLookupTable = (LookupTableInput: Object) => {
    const query = {
      createLookupTable: {
        __args: {
          lookupTableInput: { ...LookupTableInput },
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "createLookupTable", "mutation");
  };

  // Delete a Lookup Table
  const deleteLookupTable = (lookupTableName: string) => {
    const query = {
      deleteLookupTable: {
        __args: {
          lookupTableName: lookupTableName,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "deleteLookupTable", "mutation");
  };

  // Add or update a Lookup Table row.
  const upsertLookupTableRows = (lookupTableName: string, rows: any) => {
    const query = {
      upsertLookupTableRows: {
        __args: {
          lookupTableName: lookupTableName,
          rows: rows,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "upsertLookupTableRows", "mutation");
  };

  // Remove a Lookup Table row
  const removeLookupTableRows = (lookupTableName: string, rows: any) => {
    const query = {
      removeLookupTableRows: {
        __args: {
          lookupTableName: lookupTableName,
          rows: rows,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "removeLookupTableRows", "mutation");
  };

  // Set a Lookup Table backing service to active
  const setLookupTableBackingServiceActive = (lookupTableName: string, active: boolean) => {
    const query = {
      setLookupTableBackingServiceActive: {
        __args: {
          lookupTableName: lookupTableName,
          active: active,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "setLookupTableBackingServiceActive", "mutation");
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
    getLookupTable,
    getLookupTables,
    lookup,
    createLookupTable,
    deleteLookupTable,
    upsertLookupTableRows,
    removeLookupTableRows,
    setLookupTableBackingServiceActive,
    loaded,
    loading,
    errors,
  };
}
