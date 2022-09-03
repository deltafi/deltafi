/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import { reactive } from "vue";
import useNotifications from "./useNotifications";
import axios from "axios";

export default function useIngress() {
  const notify = useNotifications();

  const ingressFile = (file: File, flow: string, metadata: Record<string, string>) => {
    const result = reactive({
      did: "",
      loading: true,
      error: false,
      filename: file.name,
      flow: flow,
      percentComplete: 0
    });
    axios
      .request({
        method: "post",
        url: "/deltafile/ingress",
        data: file,
        headers: {
          "Content-Type": file.type || "application/octet-stream",
          Flow: flow,
          Filename: file.name,
          Metadata: JSON.stringify(metadata),
        },
        onUploadProgress: (progressEvent) => {
          result.percentComplete = Math.round(
            progressEvent.loaded / progressEvent.total * 100
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