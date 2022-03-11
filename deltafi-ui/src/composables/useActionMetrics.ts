import { ref, Ref } from 'vue'
import useApi from './useApi'

export default function useActionMetrics() {
  const { response, get, loading, loaded, errors } = useApi();
  const endpoint: string = 'metrics/action';
  const data: Ref<object> = ref([]);

  const fetch = async (paramsObj: Record<string, string>) => {
    try {
      const params = new URLSearchParams(paramsObj);
      await get(endpoint, params);
      data.value = response.value.actions;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch, errors };
}