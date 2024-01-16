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
  return [
    {
      id: "65a6a4a3b387934f5e404dac",
      reason: "Mock Snapshot",
      created: "2024-01-16T15:45:39.526Z",
      deletePolicies: {
        timedPolicies: [],
        diskSpacePolicies: []
      },
      flowAssignmentRules: [],
      resumePolicies: [
        {
          id: "65a5ab049312655fd9981526",
          name: "System Default: No egress flow configured",
          errorSubstring: null,
          flow: null,
          action: "NoEgressFlowConfiguredAction",
          actionType: null,
          maxAttempts: 20,
          backOff: {
            delay: 60,
            maxDelay: 300,
            multiplier: null,
            random: true
          }
        },
        {
          id: "65a5ab049312655fd9981527",
          name: "System Default: Storage read error",
          errorSubstring: "Failed to load content from storage",
          flow: null,
          action: null,
          actionType: null,
          maxAttempts: 20,
          backOff: {
            delay: 60,
            maxDelay: 300,
            multiplier: null,
            random: true
          }
        }
      ],
      deltaFiProperties: {
        systemName: "DeltaFi",
        requeueSeconds: 300,
        autoResumeCheckFrequency: "PT1M",
        coreServiceThreads: 16,
        coreInternalQueueSize: 64,
        scheduledServiceThreads: 8,
        ui: {
          useUTC: true,
          deltaFileLinks: [],
          externalLinks: [],
          topBar: {
            textColor: null,
            backgroundColor: null
          },
          securityBanner: {
            enabled: false,
            text: null,
            textColor: null,
            backgroundColor: null
          }
        },
        delete: {
          ageOffDays: 13,
          frequency: "PT10M",
          policyBatchSize: 1000
        },
        ingress: {
          enabled: true,
          diskSpaceRequirementInMb: 1000
        },
        metrics: {
          enabled: true
        },
        plugins: {
          imageRepositoryBase: "docker.io/deltafi/",
          imagePullSecret: null
        },
        checks: {
          actionQueueSizeThreshold: 10,
          contentStoragePercentThreshold: 90
        },
        inMemoryQueueSize: 5000,
        setProperties: []
      },
      pluginImageRepositories: [],
      installedPlugins: [
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-passthrough",
          version: "1.1.16-SNAPSHOT"
        },
        {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "1.1.16-SNAPSHOT"
        },
        {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "1.1.16-SNAPSHOT"
        }
      ],
      pluginVariables: [],
      transformFlows: [
        {
          name: "detect-media-type",
          running: false,
          testMode: false,
          maxErrors: -1,
          expectedAnnotations: []
        }
      ],
      normalizeFlows: [
        {
          name: "split-lines-passthrough",
          running: false,
          testMode: false,
          maxErrors: -1
        },
        {
          name: "decompress-and-merge",
          running: false,
          testMode: false,
          maxErrors: -1
        },
        {
          name: "smoke",
          running: false,
          testMode: false,
          maxErrors: -1
        },
        {
          name: "decompress-to-list",
          running: false,
          testMode: false,
          maxErrors: -1
        },
        {
          name: "passthrough",
          running: false,
          testMode: false,
          maxErrors: -1
        },
        {
          name: "decompress-passthrough",
          running: false,
          testMode: false,
          maxErrors: -1
        }
      ],
      enrichFlows: [
        {
          name: "artificial-enrichment",
          running: false
        }
      ],
      egressFlows: [
        {
          name: "compress",
          running: false,
          testMode: false,
          expectedAnnotations: []
        },
        {
          name: "passthrough",
          running: false,
          testMode: false,
          expectedAnnotations: []
        },
        {
          name: "smoke",
          running: false,
          testMode: false,
          expectedAnnotations: []
        }
      ],
      pluginCustomizationConfigs: []
    }
  ]
};

export default () => {
  return {
    getSystemSnapshots: generateData()
  }
}