/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
import useCurrentUser from "./useCurrentUser";
import axios from "axios";

export default function useIngress() {
  const notify = useNotifications();
  const { currentUser } = useCurrentUser();

  const ingressFile = (file: File, metadata: Record<string, string>, dataSource: string) => {
    const result = reactive({
      dids: [],
      loading: true,
      error: false,
      filename: file.name,
      dataSource: dataSource,
      percentComplete: 0,
    });

    const buildHeader = () => {
      const headerObject: any = {};
      headerObject["Content-Type"] = file.type || "application/octet-stream";
      headerObject["DataSource"] = dataSource;
      headerObject["Filename"] = file.name;
      headerObject["Metadata"] = JSON.stringify({
        ...metadata,
        uploadedBy: currentUser.name
      });
      return headerObject;
    };

    axios
      .request({
        method: "post",
        url: "/api/v2/deltafile/ingress",
        data: file,
        headers: buildHeader(),
        onUploadProgress: (progressEvent: any) => {
          result.percentComplete = Math.round((progressEvent.loaded / progressEvent.total) * 100);
        },
      })
      .then((res) => {
        result.dids = res.data.toString().split(',');
        result.loading = false;
        notify.success("Ingress successful", file.name);
      })
      .catch((error) => {
        result.loading = false;
        result.error = true;
        console.error(error);
        notify.error(`Failed to ingress ${file.name}`, error);
      });
    return result;
  };

  return { ingressFile };
}
