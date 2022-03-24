import { reactive } from "vue";
import useNotifications from "./useNotifications";
import axios from "axios";

export default function useIngress() {
  const notify = useNotifications();

  const ingressFile = async (file: File, flow: string, metadata: Record<string, string>) => {
    const result = reactive({
      did: "",
      loading: true,
      error: false,
      filename: file.name,
      flow: flow,
      percentComplete: 0
    });
    await axios
      .request({
        method: "post",
        url: "/deltafile/ingress",
        data: file,
        headers: {
          "Content-Type": file.type,
          Flow: flow,
          Filename: file.name,
          Metadata: JSON.stringify(metadata),
        },
        onUploadProgress: (progressEvent) => {
          result.percentComplete = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
        }
      })
      .then((res) => {
        result.did = res.data.toString();
        result.loading = false;
        notify.success("Ingress successful", file.name)
      })
      .catch((error) => {
        result.loading = false;
        result.error = true;
        console.error(error.response.data);
        notify.error(`Failed to ingress ${file.name}`, error.response.data)
      });
    return result;
  }

  return { ingressFile };
}