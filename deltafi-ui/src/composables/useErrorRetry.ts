import useGraphQL from './useGraphQL'

export default function useErrorRetry() {
  const { response, queryGraphQL } = useGraphQL();

  const buildRetryQuery = (dids: Array<string>) => {
    return {
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
  };

  const retry = async (dids: Array<string>) => {
    await queryGraphQL(buildRetryQuery(dids));
    return Promise.resolve(response);
  };

  return {
    retry,
  };
}