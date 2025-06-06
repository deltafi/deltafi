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
  return JSON.stringify(
    {
      "did": "27186720-723a-4f82-a5ab-2fff441b2c9b",
      "parentDids": [],
      "childDids": [],
      "requeueCount": 0,
      "ingressBytes": 1461,
      "totalBytes": 3692,
      "stage": "COMPLETE",
      "actions": [
        {
          "name": "IngressAction",
          "type": "INGRESS",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.046Z",
          "queued": null,
          "start": null,
          "stop": null,
          "modified": "2022-09-29T17:52:03.090Z",
          "errorCause": null,
          "errorContext": null,
          "content": [
            {
              "name": "cve-in-exploit-target.min.xml",
              "segments": [
                {
                  "uuid": "ce3c86bd-ffce-4146-8d5b-413dde392890",
                  "offset": 0,
                  "size": 1461,
                  "did": "6e45a930-5d54-427a-a78c-f237e1edfadc"
                }
              ],
              "mediaType": "text/xml"
            }
          ],
          "metadata": {},
          "deleteMetadataKeys": []
        },
        {
          "name": "stix1_x.Stix1_xTo2_1TransformAction",
          "type": "TRANSFORM",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.090Z",
          "queued": "2022-09-29T17:52:03.090Z",
          "start": "2022-09-29T17:52:03.096Z",
          "stop": "2022-09-29T17:52:03.308Z",
          "modified": "2022-09-29T17:52:03.312Z",
          "errorCause": null,
          "errorContext": null,
          "content": [
            {
              "name": null,
              "segments": [
                {
                  "uuid": "63b580d1-36b7-4078-bb0e-9f9eb4f00f35",
                  "offset": 0,
                  "size": 904,
                  "did": "6e45a930-5d54-427a-a78c-f237e1edfadc"
                }
              ],
              "mediaType": "application/json"
            }
          ],
          "metadata": {
            "stixType": "bundle",
            "stixVersion": "2.1"
          },
          "deleteMetadataKeys": []
        },
        {
          "name": "stix1_x.Stix2_1LoadAction",
          "type": "LOAD",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.312Z",
          "queued": "2022-09-29T17:52:03.312Z",
          "start": "2022-09-29T17:52:03.317Z",
          "stop": "2022-09-29T17:52:03.327Z",
          "modified": "2022-09-29T17:52:03.330Z",
          "errorCause": null,
          "errorContext": null,
          "content": [
            {
              "name": null,
              "contentReference": {
                "uuid": "63b580d1-36b7-4078-bb0e-9f9eb4f00f35",
                "offset": 0,
                "size": 904,
                "did": "6e45a930-5d54-427a-a78c-f237e1edfadc",
                "mediaType": "application/json"
              }
            }
          ],
          "metadata": {},
          "deleteMetadataKeys": []
        },
        {
          "name": "stix2_1.Stix2_1DomainAction",
          "type": "DOMAIN",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.330Z",
          "queued": "2022-09-29T17:52:03.330Z",
          "start": "2022-09-29T17:52:03.334Z",
          "stop": "2022-09-29T17:52:03.418Z",
          "modified": "2022-09-29T17:52:03.421Z",
          "errorCause": null,
          "errorContext": null,
          content: [],
          metadata: {},
          deleteMetadataKeys: []
        },
        {
          "name": "stix1_x.Stix1_xFormatAction",
          "type": "FORMAT",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.421Z",
          "queued": "2022-09-29T17:52:03.421Z",
          "start": "2022-09-29T17:52:03.424Z",
          "stop": "2022-09-29T17:52:03.593Z",
          "modified": "2022-09-29T17:52:03.596Z",
          "errorCause": null,
          "errorContext": null,
          content: [
            {
              "name": "cve-in-exploit-target.min.xml",
              "uuid": "b0ceb440-8465-45e1-9b22-08a08b630833",
              "offset": 0,
              "size": 1327,
              "did": "6e45a930-5d54-427a-a78c-f237e1edfadc",
              "mediaType": "application/xml"
            }
          ],
          metadata: {
            "stixType": "bundle"
          },
          deleteMetadataKeys: []
        },
        {
          "name": "stix1_x.Stix1_xEgressAction",
          "type": "EGRESS",
          "state": "COMPLETE",
          "created": "2022-09-29T17:52:03.596Z",
          "queued": "2022-09-29T17:52:03.596Z",
          "start": "2022-09-29T17:52:03.600Z",
          "stop": "2022-09-29T17:52:03.627Z",
          "modified": "2022-09-29T17:52:03.630Z",
          "errorCause": null,
          "errorContext": null,
          content: [],
          metadata: {},
          deleteMetadataKeys: []
        }],
      "sourceInfo": {
        "filename": "cve-in-exploit-target.min.xml",
        "flow": "stix1_x",
        "metadata": {}
      },
      "annotations": {
        "stixTypes": "vulnerability"
      },
      "created": "2022-09-29T17:52:03.046Z",
      "modified": "2022-09-29T17:52:03.630Z",
      "contentDeleted": null,
      "contentDeletedReason": null,
      "errorAcknowledged": null,
      "errorAcknowledgedReason": null,
      "egressed": true,
      "filtered": false,
      "replayed": null,
      "replayDid": null
    }
  );
};

export default {
  rawDeltaFile: generateData(),
};
