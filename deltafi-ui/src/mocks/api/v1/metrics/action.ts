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
    actions: {
      ingress: {
        IngressAction: {
          bytes_in: 10116,
          files_in: 281,
        },
      },
      transform: {
        "decompress-passthrough.DecompressPassthroughTransformAction1": {
          files_completed: 100,
          files_in: 300,
        },
        "decompress-passthrough.DecompressPassthroughTransformAction2": {
          files_completed: 2500,
          files_in: 30000,
        },
        "passthrough.PassthroughTransformAction": {},
        "smoke.SmokeTransformAction": {
          files_completed: 284,
          files_in: 284,
        },
        "stix1_x.Stix1_xTo2_1TransformAction": {},
      },
      load: {
        "decompress-passthrough.DecompressPassthroughLoadAction": {},
        "passthrough.PassthroughLoadAction": {},
        "smoke.SmokeLoadAction": {
          files_completed: 284,
          files_in: 284,
        },
        "stix1_x.Stix2_1LoadAction": {},
        "stix2_1.Stix2_1LoadAction": {},
      },
      enrich: {
        "stix2_1.Stix2_1DomainAction": {
          files_completed: 284,
          files_in: 284,
        },
        "artificial-enrichment.BinaryEnrichAction": {
          files_completed: 284,
          files_in: 284,
        },
      },
      format: {
        "error.ErrorFormatAction": {},
        "passthrough.PassthroughFormatAction": {},
        "smoke.SmokeFormatAction": {
          files_completed: 284,
          files_in: 284,
        },
        "stix1_x.Stix1_xFormatAction": {},
        "stix2_1.Stix2_1FormatAction": {},
      },
      validate: {
        "passthrough.PassthroughValidateAction": {},
        "smoke.SmokeValidateAction": {
          files_completed: 284,
          files_in: 284,
        },
      },
      egress: {
        "error.ErrorEgressAction": {},
        "passthrough.PassthroughEgressAction": {},
        "smoke.SmokeEgressAction": {
          bytes_out: 10044,
          files_completed: 279,
          files_in: 279,
          files_out: 279,
        },
        "stix1_x.Stix1_xEgressAction": {},
        "stix2_1.Stix2_1EgressAction": {},
      },
    },
    timestamp: "2022-06-23 07:57:51 +0000",
  };
};

export default generateData();
