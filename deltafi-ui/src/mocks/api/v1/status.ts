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

const generateData = () => {
  return {
    status: {
      code: 0,
      color: "green",
      state: "Healthy",
      checks: [
        {
          description: "Mock Action Queue Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
        {
          description: "Kubernetes Deployment Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
        {
          description: "Kubernetes Ingress Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
        {
          description: "Kubernetes Pod Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
        {
          description: "Kubernetes Service Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
        {
          description: "Kubernetes Storage Check",
          code: 0,
          message: "",
          timestamp: "2021-2023-02-25 20:50:21 +0000",
        },
      ],
    },
  };
};

export default generateData();
