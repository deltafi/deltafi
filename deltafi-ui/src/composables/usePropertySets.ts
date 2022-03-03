import { ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function usePropertySets() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetchQuery = {
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

  const fetch = async () => {
    try {
      await queryGraphQL(fetchQuery, "getPropertySets");
      data.value = response.value.data.getPropertySets;
    } catch {
      // Continue regardless of error
    }
  }

  const update = async (updates: Array<Object>) => {
    const query = {
      updateProperties: {
        __args: {
          updates: updates,
        },
      },
    };
    try {
      await queryGraphQL(query, "updatePropertySets", "mutation");
      data.value = response.value.data;
    } catch {
      // Continue regardless of error
    }
  }


  return { data, loading, loaded, fetch, update, errors };
}