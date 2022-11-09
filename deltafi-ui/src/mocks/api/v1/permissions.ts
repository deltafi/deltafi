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
  return [
    {
      name: "DeltaFileMetadataView",
      description: "Grants the ability to query for and view DeltaFiles",
      category: "DeltaFiles",
    },
    {
      name: "DeltaFileContentView",
      description: "Grants the ability to view DeltaFile content",
      category: "DeltaFiles",
    },
    {
      name: "DeltaFileReplay",
      description: "Grants the ability to replay DeltaFiles",
      category: "DeltaFiles",
    },
    {
      name: "DeltaFileResume",
      description: "Grants the ability to resume DeltaFiles in an ERROR stage",
      category: "DeltaFiles",
    },
    {
      name: "DeltaFileIngress",
      description: "Grants the ability to ingress DeltaFiles",
      category: "DeltaFiles",
    },
    {
      name: "DeletePolicyCreate",
      description: "Grants the ability to create Delete Policies",
      category: "DeletePolicy",
    },
    {
      name: "DeletePolicyDelete",
      description: "Grants the ability to delete Delete Policies",
      category: "DeletePolicy",
    },
    {
      name: "DeletePolicyRead",
      description: "Grants the ability to view Delete Policies",
      category: "DeletePolicy",
    },
    {
      name: "DeletePolicyUpdate",
      description: "Grants the ability to edit Delete Policies",
      category: "DeletePolicy",
    },
  ];
};

export default generateData();
