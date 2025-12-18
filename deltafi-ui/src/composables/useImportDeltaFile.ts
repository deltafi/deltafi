/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

export interface ImportResult {
  filename: string;
  loading: boolean;
  error: boolean;
  percentComplete: number;
  count: number | null;
  bytes: number | null;
}

export default function useImportDeltaFile() {
  const notify = useNotifications();

  const importDeltaFile = (file: File): ImportResult => {
    const result = reactive<ImportResult>({
      filename: file.name,
      loading: true,
      error: false,
      percentComplete: 0,
      count: null,
      bytes: null,
    });

    axios
      .request({
        method: "post",
        url: "/api/v2/deltafile/import",
        data: file,
        headers: {
          "Content-Type": "application/x-tar",
        },
        onUploadProgress: (progressEvent: any) => {
          result.percentComplete = Math.round((progressEvent.loaded / progressEvent.total) * 100);
        },
      })
      .then((res) => {
        result.count = res.data.count;
        result.bytes = res.data.bytes;
        result.loading = false;
        notify.success("Import successful", `Imported ${res.data.count} DeltaFile(s) from ${file.name}`);
      })
      .catch((error) => {
        result.loading = false;
        result.error = true;
        console.error(error);

        let errorMessage = "Unknown error occurred";
        let statusCode = "";

        if (error.response?.status) {
          statusCode = `HTTP ${error.response.status}`;

          if (error.response?.data) {
            const serverMessage = typeof error.response.data === 'string' ? error.response.data : error.response.data.message || error.response.data.error || "Server error";
            errorMessage = `${statusCode}: ${serverMessage}`;
          } else {
            errorMessage = `${statusCode}: ${error.response.statusText || "Server error"}`;
          }
        } else if (error.message) {
          errorMessage = error.message;
        }

        notify.error(`Failed to import ${file.name}`, errorMessage);
      });

    return result;
  };

  return { importDeltaFile };
}
