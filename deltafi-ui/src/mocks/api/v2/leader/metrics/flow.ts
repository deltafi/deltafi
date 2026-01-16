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

const generateData = () => {
  return {
    "deltafi-member-1": {
      ingressByDataSource: {
        "smoke-test-data-source": 1500,
        "passthrough-data-source": 800,
      },
      egressByDataSink: {
        "smoke-test-data-sink": 1400,
        "passthrough-data-sink": 750,
      },
      storageByService: {
        minio: 1073741824,
      },
      deletedByPolicy: {
        "default-delete-policy": 200,
      },
    },
    "deltafi-member-2": {
      ingressByDataSource: {
        "smoke-test-data-source": 1200,
        "passthrough-data-source": 600,
      },
      egressByDataSink: {
        "smoke-test-data-sink": 1100,
        "passthrough-data-sink": 550,
      },
      storageByService: {
        minio: 536870912,
      },
      deletedByPolicy: {
        "default-delete-policy": 150,
      },
    },
    "deltafi-member-3": {
      ingressByDataSource: {
        "smoke-test-data-source": 500,
      },
      egressByDataSink: {
        "smoke-test-data-sink": 450,
      },
      storageByService: {
        minio: 268435456,
      },
      deletedByPolicy: {
        "default-delete-policy": 50,
      },
    },
  };
};

export default generateData();
