import { ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function usePropertySets() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetchQuery = {
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

  const fetch = async () => {
    try {
      await queryGraphQL(fetchQuery);
      data.value = response.value.data.getPropertySets;
    } catch {
      // Continue regardless of error
    }
  }

  const update = async (updates: Array<Object>) => {
    const query = 'mutation($updates: [PropertyUpdate]!) { updateProperties(updates: $updates) }'
    const variables = {
      updates: updates
    }
    const body = JSON.stringify({ query: query, variables: variables })
    try {
      await queryGraphQL(body);
      data.value = response.value.data;
    } catch {
      // Continue regardless of error
    }
  }


  return { data, loading, loaded, fetch, update, errors };
}