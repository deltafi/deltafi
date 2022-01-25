import { query } from 'express';
import { EnumType, jsonToGraphQLQuery } from 'json-to-graphql-query';
export default class GraphQLService {
  basePath: string;

  constructor(basePath: string = '/graphql') {
    this.basePath = basePath;
  }

  query(queryString: string) {
    return fetch(this.basePath, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: queryString
    }).then(res => {
      return res.json();
    });
  }

  getPropertySets() {
    const getPropertySetsQuery = {
      query: {
        getPropertySets: {
          id: true,
          displayName: true,
          description: true,
          properties: {
            key: true,
            value: true,
            hidden: true,
            editable: true,
            refreshable: true,
            description: true,
            propertySource: true
          }
        }
      }
    }
    return this.convertJsonToGraphQLQuery(getPropertySetsQuery);
  }

  updateProperties(updates: Array<Object>) {
    const query = 'mutation($updates: [PropertyUpdate]!) { updateProperties(updates: $updates) }'
    const variables = {
      updates: updates
    }
    const body = JSON.stringify({ query: query, variables: variables })
    return this.query(body);
  }

  convertJsonToGraphQLQuery(queryString: Object) {
    const graphQLquery = jsonToGraphQLQuery(queryString, { pretty: true });
    return this.query(JSON.stringify({ query: graphQLquery }));
  }

  // This function allows for the querying of enum types and returns there associated enums.
  getEnumValuesByEnumType(enumType: string) {
    const data = JSON.stringify({
      query: `{
        __type(name: "${enumType}") {
          enumValues {
            name
          }
        }
      }`,
    });
    return this.query(data);
  }

  getConfigByType(typeParam: string) {
    const typeSearchParams = {
      query: {
        deltaFiConfigs: {
          __args: {
            configQuery: {
              configType: new EnumType(typeParam)
            }
          },
          name: true
        }
      }
    }
    return this.convertJsonToGraphQLQuery(typeSearchParams);
  }

  getDeltaFiFileNames(startD: Date, endD: Date, fileName?: string, stageName?: string, actionName?: string, flowName?: string) {
    const flowTypeSearchParams = {
      query: {
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
              modifiedBefore: endD.toISOString(),
              modifiedAfter: startD.toISOString()
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
      }
    };
    return this.convertJsonToGraphQLQuery(flowTypeSearchParams);
  }

  getRecordCount(startD: Date, endD: Date, fileName?: string, stageName?: string, actionName?: string, flowName?: string) {
    const searchRecordCountParams = {
      query: {
        deltaFiles: {
          __args: {
            offset: 0,
            limit: 0,
            filter: {
              sourceInfo: {
                flow: flowName,
                filename: fileName
              },
              stage: stageName ? new EnumType(stageName) : null,
              actions: actionName,
              modifiedBefore: endD.toISOString(),
              modifiedAfter: startD.toISOString()
            },
            orderBy: {
              direction: new EnumType('DESC'),
              field: 'modified'
            }
          },
          totalCount: true,
        }
      }
    };
    return this.convertJsonToGraphQLQuery(searchRecordCountParams);
  }

  getErrorCount(since: Date = new Date(0)) {
    const searchParams = {
      query: {
        deltaFiles: {
          __args: {
            filter: {
              stage: new EnumType('ERROR'),
              errorAcknowledged: false,
              modifiedAfter: since.toISOString()
            },
          },
          totalCount: true,
        }
      }
    };
    return this.convertJsonToGraphQLQuery(searchParams);
  }

  getErrors(showAcknowledged: boolean, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, flowName?: string) {
    const searchParams = {
      query: {
        deltaFiles: {
          __args: {
            limit: perPage,
            offset: offSet,
            filter: {
              sourceInfo: {
                flow: flowName
              },
              stage: new EnumType('ERROR'),
              errorAcknowledged: showAcknowledged
            },
            orderBy: {
              direction: new EnumType(sortDirection),
              field: sortBy,
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
            actions: {
              name: true,
              created: true,
              modified: true,
              errorCause: true,
              errorContext: true,
              state: true,
            },
            sourceInfo: {
              filename: true,
              flow: true,
            },
            errorAcknowledged: true,
            errorAcknowledgedReason: true
          }
        }
      }
    };
    return this.convertJsonToGraphQLQuery(searchParams);
  }


  getDeltaFileSearchData(startD: Date, endD: Date, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, fileName?: string, stageName?: string, actionName?: string, flowName?: string) {
    const searchParams = {
      query: {
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
              modifiedBefore: endD.toISOString(),
              modifiedAfter: startD.toISOString()
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
      }
    };
    return this.convertJsonToGraphQLQuery(searchParams);
  }

  postErrorRetry(dids: Array<string>) {
    const postString = {
      mutation: {
        retry: {
          __args: {
            dids: dids
          },
          did: true,
          success: true,
          error: true
        }
      }
    };
    return this.convertJsonToGraphQLQuery(postString);
  }

  acknowledgeErrors(dids: Array<string>, reason: string) {
    const postString = {
      mutation: {
        acknowledge: {
          __args: {
            dids: dids,
            reason: reason
          },
          did: true,
          success: true,
          error: true
        },
      }
    };
    return this.convertJsonToGraphQLQuery(postString);
  }

  getDeltaFile(did: string) {
    const searchParams = {
      query: {
        deltaFile: {
          __args: {
            did: did,
          },
          did: true,
          sourceInfo: {
            filename: true,
            flow: true,
            metadata: {
              key: true,
              value: true,
            },
          },
          stage: true,
          created: true,
          modified: true,
          actions: {
            name: true,
            state: true,
            created: true,
            modified: true,
            errorCause: true,
            errorContext: true,
          },
          domains: {
            name: true,
            value: true,
            mediaType: true,
          },
          enrichment: {
            name: true,
            value: true,
            mediaType: true,
          },
          formattedData: {
            filename: true,
            metadata: {
              key: true,
              value: true,
            },
            formatAction: true,
            egressActions: true,
            contentReference: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
              mediaType: true,
            },
          },
          protocolStack: {
            action: true,
            metadata: {
              key: true,
              value: true,
            },
            contentReference: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
              mediaType: true,
            },
          },
          markedForDelete: true,
          markedForDeleteReason: true,
          errorAcknowledged: true,
          errorAcknowledgedReason: true,
        },
      }
    };
    return this.convertJsonToGraphQLQuery(searchParams);
  }

  getFlowConfigYaml() {
    const data = {
      query: {
        exportConfigAsYaml: true
      },
    }
    return this.convertJsonToGraphQLQuery(data);
  }
}