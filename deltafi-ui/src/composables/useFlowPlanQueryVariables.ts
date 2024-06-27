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
  subscribe: {
    condition: true,
    topic: true,
  },
  publish: {
    matchingPolicy: true,
    defaultRule: {
      defaultBehavior: true,
      topic: true
    },
    rules: {
      condition: true,
      topic: true,
    }
  },
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  maxErrors: true,
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

export const egressFlowFields = {
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

export const egressFlowPlanFields = {
  name: true,
  description: true,
  type: true,
  egressAction: {
    ...defaultActionFields,
  },
};

export const transformFlow = {
  ...sourcePluginFields,
  ...transformFlowFields,
};

export const transformFlowPlan = {
  ...sourcePluginFields,
  ...transformFlowPlanFields,
};

export const egressFlow = {
  ...sourcePluginFields,
  ...egressFlowFields,
};

export const egressFlowPlan = {
  ...sourcePluginFields,
  ...egressFlowPlanFields,
};
