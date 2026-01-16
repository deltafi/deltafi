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
    plugins: {
      Leader: [
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.47.1",
          displayName: "DeltaFi Core Actions",
          imageName: "deltafi/deltafi-core-actions",
          imageTag: "2.47.1",
        },
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-passthrough",
          version: "2.47.1",
          displayName: "DeltaFi Passthrough",
          imageName: "deltafi/deltafi-passthrough",
          imageTag: "2.47.1",
        },
      ],
      "deltafi-member-1": [
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.47.1",
          displayName: "DeltaFi Core Actions",
          imageName: "deltafi/deltafi-core-actions",
          imageTag: "2.47.1",
        },
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-passthrough",
          version: "2.47.1",
          displayName: "DeltaFi Passthrough",
          imageName: "deltafi/deltafi-passthrough",
          imageTag: "2.47.1",
        },
      ],
      "deltafi-member-2": [
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.47.1",
          displayName: "DeltaFi Core Actions",
          imageName: "deltafi/deltafi-core-actions",
          imageTag: "2.47.1",
        },
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-passthrough",
          version: "2.47.0",
          displayName: "DeltaFi Passthrough",
          imageName: "deltafi/deltafi-passthrough",
          imageTag: "2.47.0",
        },
      ],
    },
    membersNotReporting: ["deltafi-member-3"],
  };
};

export default generateData();
