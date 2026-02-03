/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

export default {
  getDeltaFiProperties: {
    systemName: "Mock DeltaFi System",
    requeueDuration: "PT30S",
    autoResumeCheckFrequency: "PT1M",
    coreServiceThreads: 16,
    coreInternalQueueSize: 64,
    scheduledServiceThreads: 8,
    delete: {
      ageOffDays: 13,
      frequency: "PT10M",
      policyBatchSize: 1000,
    },
    ingress: {
      enabled: true,
      diskSpaceRequirementInMb: 500,
    },
    metrics: {
      enabled: true,
    },
    plugins: {
      imageRepositoryBase: "docker.io/deltafi",
      imagePullSecret: null,
    },
    checks: {
      actionQueueSizeThreshold: 10,
      contentStoragePercentThreshold: 90,
    },
    inMemoryQueueSize: 10,
    setProperties: [],
  },
};
