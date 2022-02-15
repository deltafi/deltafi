import { ref, Ref } from 'vue'
import useApi from './useApi'

export default function useStatus() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'status';
  const data: Ref = ref({});

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = {
        ...response.value.status,
        timestamp: response.value.timestamp
      }
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}