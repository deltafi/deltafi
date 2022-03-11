import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

export default function useDeltaFilesQueryBuilder(): {
  getDeltaFileSearchData: (startDateISOString: String, endDateISOString: String, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => any;
  getRecordCount: (startDateISOString: String, endDateISOString: String, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => any;
  getDeltaFiFileNames: (startDateISOString: String, endDateISOString: String, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => any;
  getEnumValuesByEnumType: (enumType: string) => any;
  getConfigByType: (typeParam: string) => any;
} {
  const { response, queryGraphQL } = useGraphQL();

  const getDeltaFileSearchData = (startDateISOString: String, endDateISOString: String, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: offSet,
          limit: perPage,
          filter: {
            sourceInfo: {
              flow: flowName,
              filename: fileName
            },
            stage: stageName ? new EnumType(stageName) : null,
            actions: actionName,
            modifiedAfter: startDateISOString,
            modifiedBefore: endDateISOString
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
        }
      }
    };
    return sendGraphQLQuery(query, "getDeltaFileSearchData");
  }

  const getRecordCount = (startDateISOString: String, endDateISOString: String, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: 0,
          limit: 1,
          filter: {
            sourceInfo: {
              flow: flowName,
              filename: fileName
            },
            stage: stageName ? new EnumType(stageName) : null,
            actions: actionName,
            modifiedAfter: startDateISOString,
            modifiedBefore: endDateISOString
          },
          orderBy: {
            direction: new EnumType('DESC'),
            field: 'modified'
          }
        },
        totalCount: true,
      }
    }
    return sendGraphQLQuery(query, "getRecordCount");
  }

  const getDeltaFiFileNames = (startDateISOString: String, endDateISOString: String, fileName?: string, stageName?: string, actionName?: string, flowName?: string) => {
    const query = {
      deltaFiles: {
        __args: {
          offset: 0,
          limit: 10000,
          filter: {
            sourceInfo: {
              flow: flowName,
              filename: fileName
            },
            stage: stageName ? new EnumType(stageName) : null,
            actions: actionName,
            modifiedAfter: startDateISOString,
            modifiedBefore: endDateISOString
          },
          orderBy: {
            direction: new EnumType('DESC'),
            field: 'modified'
          }
        },
        deltaFiles: {
          sourceInfo: {
            filename: true,
          }
        }
      }
    };
    return sendGraphQLQuery(query, "getDeltaFiFileNames");
  };

  const getEnumValuesByEnumType = (enumType: string) => {
    const query = `
      __type(name: "${enumType}") {
        enumValues {
          name
        }
      }`
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
    }
    return sendGraphQLQuery(query, "getConfigByType");
  }


  const sendGraphQLQuery = async (query: any, operationName: string) => {
    try {
      await queryGraphQL(query, operationName);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  }


  return {
    getDeltaFileSearchData,
    getRecordCount,
    getDeltaFiFileNames,
    getEnumValuesByEnumType,
    getConfigByType
  };
}