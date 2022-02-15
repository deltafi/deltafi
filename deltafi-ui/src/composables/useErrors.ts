import { ref } from 'vue'
import useGraphQL from './useGraphQL'
import { EnumType } from 'json-to-graphql-query';
export default function useErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetch = async (showAcknowledged: boolean, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, flowName?: string) => {
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
    await queryGraphQL(searchParams);
    data.value = response.value.data;
  };


  return { data, loading, loaded, fetch, errors };
}