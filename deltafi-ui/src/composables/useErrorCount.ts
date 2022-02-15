import { ref, readonly, Ref } from 'vue';
import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

const errorCount = ref(0)

export default function useErrorCount(): {
  errorCount: Ref<number>
  fetchErrorCount: () => void;
  fetchErrorCountSince: () => void
} {
  const { response, queryGraphQL } = useGraphQL();

  const setErrorCount = ($errorCount: number) => {
    return (errorCount.value = $errorCount);
  };

  const buildQuery= async (since: Date = new Date(0)) => {
    const query = {
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
    await queryGraphQL(query);
    return response.value.data.deltaFiles.totalCount;
  }

  const fetchErrorCount = async (since: Date = new Date(0)) => {
    const count = buildQuery(since);
    setErrorCount(await count);
  };

  const fetchErrorCountSince = async (since: Date = new Date(0)) => {
    return buildQuery(since);
  }

  return {
    errorCount: readonly(errorCount),
    fetchErrorCount,
    fetchErrorCountSince,
  };
}