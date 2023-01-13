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
    queues: [
      {
        name: "dgs",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.core.action.DropEgressAction",
        size: 1,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.core.action.FilterEgressAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.core.action.RestPostEgressAction",
        size: 8,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.core.action.SimpleErrorFormatAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.core.action.delete.DeleteAction",
        size: 29,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.passthrough.action.RoteEnrichAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.passthrough.action.RoteFormatAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.passthrough.action.RoteLoadAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.passthrough.action.RoteTransformAction",
        size: 12,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.passthrough.action.RubberStampValidateAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.stix.actions.RubberStampValidateAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.stix.actions.Stix1_XFormatAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.stix.actions.Stix2_1FormatAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.stix.actions.StixLoadAction",
        size: 1,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
      {
        name: "org.deltafi.stix.actions.StixTransformAction",
        size: 0,
        timestamp: "2021-2023-02-25 15:11:07 +0000",
      },
    ],
  };
};

export default generateData();
