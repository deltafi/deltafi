import useGraphQL from './useGraphQL'

export default function useErrorRetry() {
  const { response, queryGraphQL } = useGraphQL();

  const buildRetryQuery = (dids: Array<string>) => {
    return {
      retry: {
        __args: {
          dids: dids
        },
        did: true,
        success: true,
        error: true
      }
    };
  };

  const retry = async (dids: Array<string>) => {
    await queryGraphQL(buildRetryQuery(dids), "useErrorsRetry", "mutation");
    return Promise.resolve(response);
  };

  return {
    retry,
  };
}