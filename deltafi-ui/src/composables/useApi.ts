import { ref, Ref } from "vue";
import useNotifications from "./useNotifications";

export default function useApi(version: Number = 1) {
  const notify = useNotifications();
  const basePath: RequestInfo = `/api/v${version}`;
  const response: Ref<any> = ref({});
  const loading: Ref<Boolean> = ref(false);
  const loaded: Ref<Boolean> = ref(false);
  const errors: Ref<Array<string>> = ref([]);

  const buildURL = (endpoint: String, params: URLSearchParams = new URLSearchParams()) => {
    let url = `${basePath}/${endpoint}`;
    if (Array.from(params.keys()).length > 0) {
      url += `?${params.toString()}`;
    }
    return url;
  }

  const get = async (endpoint: String, params: URLSearchParams = new URLSearchParams(), parseJSON: boolean = true) => {
    const url = buildURL(endpoint, params);
    const req = new Request(url, { referrer: "" });
    loading.value = true;
    errors.value = [];
    try {
      const res = await fetch(req);
      if (!res.ok) {
        if ([500, 404].includes(res.status)) {
          const body = await res.json();
          notify.error("Error Received from API", body.error);
          errors.value.push(body.error)
        } else {
          throw Error(res.statusText);
        }
        return Promise.reject(res);
      }
      response.value = (parseJSON) ? await res.json() : await res.blob()
      loaded.value = true;
      return Promise.resolve(res);
    } catch (error: any) {
      errors.value.push(error)
      notify.error("Error Contacting API", error)
      return Promise.reject(error);
    } finally {
      loading.value = false;
    }
  }

  return { response, loading, loaded, errors, get, buildURL };
}