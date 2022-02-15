import { ref, Ref } from 'vue'
import useApi from './useApi'

export default function useVersions() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'versions';
  const data: Ref<Array<any>> = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value.versions;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}