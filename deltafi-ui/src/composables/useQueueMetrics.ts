import { ref, Ref } from 'vue'
import useApi from './useApi'

export default function useQueueMetrics() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'metrics/queues';
  const data: Ref<Array<any>> = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value.queues;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}