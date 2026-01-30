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

export const sourcePluginFields = {
  sourcePlugin: {
    artifactId: true,
    groupId: true,
    version: true,
  },
};

export const variableFields = {
  variables: {
    name: true,
    value: true,
    description: true,
    defaultValue: true,
    dataType: true,
  },
};

export const joinFields = {
  join: {
    maxAge: true,
    minNum: true,
    maxNum: true,
    metadataKey: true,
  },
};

export const flowStatusFields = {
  state: true,
  valid: true,
  placeholder: true,
  errors: {
    configName: true,
    errorType: true,
    message: true,
  },
};

export const defaultActionFields = {
  name: true,
  type: true,
  parameters: true,
  apiVersion: true,
};

export const transformFlowFields = {
  name: true,
  type: true,
  subscribe: {
    condition: true,
    topic: true,
  },
  publish: {
    matchingPolicy: true,
    defaultRule: {
      defaultBehavior: true,
      topic: true,
    },
    rules: {
      condition: true,
      topic: true,
    },
  },
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  transformActions: {
    ...defaultActionFields,
    ...joinFields,
  },
  ...variableFields,
};

export const transformFlowPlanFields = {
  name: true,
  description: true,
  type: true,
  transformActions: {
    ...defaultActionFields,
    ...joinFields,
  },
  egressAction: {
    ...defaultActionFields,
  },
};

export const dataSinkFields = {
  name: true,
  subscribe: {
    condition: true,
    topic: true,
  },
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  egressAction: {
    ...defaultActionFields,
  },
  ...variableFields,
  expectedAnnotations: true,
};

export const dataSinkPlanFields = {
  name: true,
  description: true,
  type: true,
  egressAction: {
    ...defaultActionFields,
  },
};

export const transformFlow = {
  ...sourcePluginFields,
  pluginReady: true,
  pluginNotReadyReason: true,
  ...transformFlowFields,
};

export const transformFlowPlan = {
  ...sourcePluginFields,
  ...transformFlowPlanFields,
};

export const dataSink = {
  ...sourcePluginFields,
  pluginReady: true,
  pluginNotReadyReason: true,
  ...dataSinkFields,
};

export const dataSinkPlan = {
  ...sourcePluginFields,
  ...dataSinkPlanFields,
};
