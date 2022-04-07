import useApi from './useApi'

export default function useStatus() {
  const { response, get, loading, loaded, errors } = useApi();
  const endpoint: string = 'status';

  const fetchStatus = async () => {
    try {
      await get(endpoint);
      return response.value.status;
    } catch {
      // Continue regardless of error
    }
  }

  return { loaded, loading, errors, fetchStatus };
}