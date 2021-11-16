import { EnumType, jsonToGraphQLQuery } from 'json-to-graphql-query';
export default class GraphQLService {
  basePath: string;

  constructor(basePath: string = '/graphql') {
    this.basePath = basePath;
  }

  query(queryString: Object) {
    const graphQLquery = jsonToGraphQLQuery(queryString, { pretty: true });
    return fetch(this.basePath, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ query: graphQLquery })
    }).then(res => {
      return res.json();
    });
  }

  getFlowsByType(flowType: string) {
    const flowTypeSearchParams = {
      query: {
        deltaFiConfigs: {
          __args: {
            configQuery: {
              configType: new EnumType(flowType)
            }  
          },
          name: true
        }
      }
    }
    return this.query(flowTypeSearchParams);
  }

  getErrors(startD: Date, endD: Date, flowEventName?: string) {
    const searchParams = {
      query: {
        deltaFiles: {
          __args: {
            offset: 0,
            limit: 50,
            filter: {
              sourceInfo: {
                flow: flowEventName
              },
              stage: new EnumType('ERROR'),
              modifiedBefore: endD.toISOString(),
              modifiedAfter: startD.toISOString()
            },
            orderBy: {
              direction: new EnumType('DESC'),
              field: new EnumType('modified')
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
            protocolStack: {
              type: true,
              metadata: {
                key: true,
                value: true,
              },
              objectReference: {
                name: true,
                bucket: true,
                offset: true,
                size: true,
              },
            },
            sourceInfo: {
              filename: true,
              flow: true,
              metadata: {
                key: true,
                value: true,
              },
            },
            enrichment: {
              key: true,
              value: true,
            },
            domains: {
              key: true,
              value: true,
            },
            formattedData: {
              filename: true,
              formatAction: true,
              objectReference: {
                bucket: true,
                offset: true,
                name: true,
                size: true,
              },
            }
          }
        }
      }
    };
    return this.query(searchParams);
  }

  postErrorRetry(did: string) {
    const postString = {
      mutation: {
        retry: {
          __args: {
            did: did
          },
          did
        },
      }

    };
    return this.query(postString);
  }
}