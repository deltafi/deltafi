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

export const collectFields = {
  collect: {
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
  subscriptions: {
    condition: true,
    topics: true,
  },
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  maxErrors: true,
  transformActions: {
    ...defaultActionFields,
    ...collectFields,
  },
  egressAction: {
    ...defaultActionFields,
  },
  ...variableFields,
  expectedAnnotations: true,
};

export const transformFlowPlanFields = {
  name: true,
  description: true,
  type: true,
  transformActions: {
    ...defaultActionFields,
    ...collectFields,
  },
  egressAction: {
    ...defaultActionFields,
  },
};

export const normalizeFlowFields = {
  name: true,
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  maxErrors: true,
  transformActions: {
    ...defaultActionFields,
    ...collectFields,
  },
  loadAction: {
    ...defaultActionFields,
    ...collectFields,
  },
  ...variableFields,
};

export const normalizeFlowPlanFields = {
  name: true,
  description: true,
  type: true,
  transformActions: {
    ...defaultActionFields,
    ...collectFields,
  },
  loadAction: {
    ...defaultActionFields,
    ...collectFields,
  },
};

export const enrichFlowFields = {
  name: true,
  description: true,
  flowStatus: {
    ...flowStatusFields,
  },
  domainActions: {
    ...defaultActionFields,
    requiresDomains: true,
  },
  enrichActions: {
    ...defaultActionFields,
    requiresDomains: true,
    requiresEnrichments: true,
    requiresMetadataKeyValues: {
      key: true,
      value: true,
    },
  },
  ...variableFields,
};

export const enrichFlowPlanFields = {
  name: true,
  description: true,
  type: true,
  domainActions: {
    ...defaultActionFields,
    requiresDomains: true,
  },
  enrichActions: {
    ...defaultActionFields,
    requiresDomains: true,
    requiresEnrichments: true,
    requiresMetadataKeyValues: {
      key: true,
      value: true,
    },
  },
};

export const egressFlowFields = {
  name: true,
  description: true,
  flowStatus: {
    ...flowStatusFields,
    testMode: true,
  },
  includeNormalizeFlows: true,
  excludeNormalizeFlows: true,
  formatAction: {
    ...defaultActionFields,
    ...collectFields,
    requiresDomains: true,
    requiresEnrichments: true,
  },
  validateActions: {
    ...defaultActionFields,
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
  includeNormalizeFlows: true,
  excludeNormalizeFlows: true,
  formatAction: {
    ...defaultActionFields,
    ...collectFields,
    requiresDomains: true,
    requiresEnrichments: true,
  },
  validateActions: {
    ...defaultActionFields,
  },
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

export const normalizeFlow = {
  ...sourcePluginFields,
  ...normalizeFlowFields,
};

export const normalizeFlowPlan = {
  ...sourcePluginFields,
  ...normalizeFlowPlanFields,
};

export const enrichFlow = {
  ...sourcePluginFields,
  ...enrichFlowFields,
};

export const enrichFlowPlan = {
  ...sourcePluginFields,
  ...enrichFlowPlanFields,
};

export const egressFlow = {
  ...sourcePluginFields,
  ...egressFlowFields,
};

export const egressFlowPlan = {
  ...sourcePluginFields,
  ...egressFlowPlanFields,
};
