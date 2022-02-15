import { ref, Ref } from 'vue'
import useApi from './useApi'

interface ContentReference {
  did: string;
  uuid: number;
  size: number;
  offset: number;
  mediaType: string;
  filename: string;
}

export default function useContent() {
  const { response, get, loading, loaded, buildURL, errors } = useApi();
  const endpoint: string = 'content';
  const data: Ref<Blob | undefined> = ref();

  const fetch = async (contentReference: ContentReference) => {
    const params = new URLSearchParams(contentReference as any);
    await get(endpoint, params, false);
    data.value = response.value;
  }

  const downloadURL = (contentReference: ContentReference) => {
    const params = new URLSearchParams(contentReference as any);
    return buildURL(endpoint, params);
  }

  return { data, loaded, loading, fetch, downloadURL, errors };
}