import { ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function useAcknowledgeErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const post = async (dids: Array<string>, reason: string) => {
    const query = {
      acknowledge: {
        __args: {
          dids: dids,
          reason: reason
        },
        did: true,
        success: true,
        error: true
      },
    };
    await queryGraphQL(query, "getAcknowledgeErrors", "mutation");
    data.value = response.value.data;
  };

  return { data, loading, loaded, post, errors };
}