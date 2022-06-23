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

const generateData = () => {
  return {
    "name": "decompress-passthrough",
    "description": "Flow that passes data through unchanged",
    "type": "compressedBinary",
    "sourcePlugin": {
      "groupId": "org.deltafi.passthrough",
      "artifactId": "deltafi-passthrough",
      "version": "0.95.4"
    },
    "flowStatus": {
      "state": "RUNNING",
      "errors": []
    },
    "transformActions": [
      {
        "name": "decompress-passthrough.DecompressPassthroughTransformAction1",
        "consumes": "compressedBinary",
        "produces": "binary",
        "parameters": {
          "decompressionType": "auto"
        },
        "requiresDomains": [
          "binary",
          "non-binary"
        ],
        "type": "org.deltafi.core.action.DecompressionTransformAction",
        "apiVersion": null
      },
      {
        "name": "decompress-passthrough.DecompressPassthroughTransformAction2",
        "consumes": "compressedBinary",
        "produces": "binary",
        "parameters": {
          "decompressionType": "auto"
        },
        "type": "org.deltafi.core.action.DecompressionTransformAction",
        "apiVersion": null
      }
    ],
    "loadAction": {
      "name": "decompress-passthrough.DecompressPassthroughLoadAction",
      "consumes": "binary",
      "apiVersion": null,
      "parameters": {
        "reinjectFlow": "passthrough"
      }
    },
    "variables": []
  }
};

export default {
  "getIngressFlow": generateData()
}