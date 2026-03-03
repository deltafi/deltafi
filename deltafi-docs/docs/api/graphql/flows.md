---
# ABOUTME: Documents GraphQL queries and mutations for flow and topic management.
# ABOUTME: Covers flow plans, flow state, data sources, test mode, and publish-subscribe topics.
---

# Flows GraphQL API

Queries and mutations for managing flows — the processing pipelines that DeltaFiles pass through. This includes flow plans (templates), deployed flow instances, data source configuration, and publish-subscribe topics.

## Flow Plan Queries

### Get Flow Plans

```graphql
# Get all flow plans
query {
  getAllFlowPlans {
    dataSinkPlans { name description sourcePlugin { artifactId } }
    restDataSources { name description topic }
    timedDataSources { name description topic cronSchedule }
    onErrorDataSources { name description topic errorMessageRegex }
    transformPlans { name description }
  }
}

# Get all system plugin flow plans
query {
  getAllSystemFlowPlans {
    dataSinkPlans { name }
    transformPlans { name }
  }
}
```

**Permission:** `FlowView`

### Get Individual Flow Plans

```graphql
query { getDataSinkPlan(planName: "my-sink") { name description egressAction { name type } } }
query { getTransformFlowPlan(planName: "my-transform") { name transformActions { name type } publish { rules { topic } } } }
query { getRestDataSourcePlan(planName: "my-source") { name topic } }
query { getTimedDataSourcePlan(planName: "my-timed") { name cronSchedule timedIngressAction { name } } }
query { getOnErrorDataSourcePlan(planName: "my-error-source") { name errorMessageRegex } }
```

**Permission:** `FlowView`

### Validate Flows

Validate flow configuration without changing state.

```graphql
query { validateDataSink(flowName: "my-sink") { flowStatus { valid errors { message errorType } } } }
query { validateTransformFlow(flowName: "my-transform") { flowStatus { valid errors { message } } } }
query { validateRestDataSource(name: "my-source") { flowStatus { valid } } }
query { validateTimedDataSource(name: "my-timed") { flowStatus { valid } } }
query { validateOnErrorDataSource(name: "my-error-source") { flowStatus { valid } } }
```

**Permission:** `FlowValidate`

## Deployed Flow Queries

### Get Individual Flows

```graphql
query {
  getDataSink(flowName: "my-sink") {
    name
    description
    flowStatus { state testMode valid }
    subscribe { topic condition }
    egressAction { name type parameters }
    variables { name value }
  }
}

query {
  getTransformFlow(flowName: "my-transform") {
    name
    flowStatus { state }
    subscribe { topic }
    transformActions { name type }
    publish { matchingPolicy rules { topic condition } defaultRule { defaultBehavior } }
  }
}

query { getRestDataSource(name: "my-source") { name flowStatus { state } topic maxErrors rateLimit { unit maxAmount durationSeconds } } }
query { getTimedDataSource(name: "my-timed") { name cronSchedule lastRun nextRun memo flowStatus { state } } }
query { getOnErrorDataSource(name: "my-error") { name errorMessageRegex sourceFilters { flowType flowName } } }
```

**Permission:** `FlowView`

### List Flows

```graphql
# All flows organized by plugin
query {
  getFlows {
    sourcePlugin { groupId artifactId version }
    dataSinks { name flowStatus { state } }
    transformFlows { name flowStatus { state } }
    restDataSources { name flowStatus { state } }
    timedDataSources { name flowStatus { state } }
    onErrorDataSources { name flowStatus { state } }
  }
}

# All running flows
query { getRunningFlows { dataSink { name } transform { name } restDataSource { name } timedDataSource { name } } }

# All flows regardless of state
query { getAllFlows { dataSink { name flowStatus { state } } transform { name } } }

# Flow names only
query { getFlowNames(state: RUNNING) { dataSink transform restDataSource timedDataSource onErrorDataSource } }

# Find flows by tags
query { findFlowsByTags(filter: { all: ["production"], types: [TRANSFORM, DATA_SINK] }) { transform { name } dataSink { name } } }
```

**Permission:** `FlowView`

### Data Source Error Monitoring

```graphql
query {
  dataSourceErrorsExceeded {
    name
    currErrors
    maxErrors
  }
}
```

**Permission:** `FlowView`

## Flow Plan Mutations

### Save Flow Plans

```graphql
mutation {
  saveTransformFlowPlan(transformFlowPlan: {
    name: "my-transform"
    type: "TRANSFORM"
    description: "Transforms incoming data"
    subscribe: [{ topic: "raw-data" }]
    transformActions: [
      { name: "MyTransformAction", type: "org.example.MyTransform" }
    ]
    publish: {
      matchingPolicy: FIRST_MATCHING
      defaultRule: { defaultBehavior: ERROR }
      rules: [{ topic: "processed-data" }]
    }
  }) {
    name
    flowStatus { state valid }
  }
}

mutation {
  saveDataSinkPlan(dataSinkPlan: {
    name: "my-sink"
    type: "DATA_SINK"
    description: "Egresses data to external system"
    subscribe: [{ topic: "processed-data" }]
    egressAction: { name: "MyEgressAction", type: "org.example.MyEgress" }
  }) {
    name
  }
}
```

Similar mutations exist for all plan types: `saveRestDataSourcePlan`, `saveTimedDataSourcePlan`, `saveOnErrorDataSourcePlan`.

**Permission:** `FlowPlanCreate`

### Remove Flow Plans

```graphql
mutation { removeTransformFlowPlan(name: "my-transform") }
mutation { removeDataSinkPlan(name: "my-sink") }
mutation { removeRestDataSourcePlan(name: "my-source") }
mutation { removeTimedDataSourcePlan(name: "my-timed") }
mutation { removeOnErrorDataSourcePlan(name: "my-error-source") }
```

**Permission:** `FlowPlanDelete`

### Bulk Save Flow Plans

Replace all flow plans at once.

```graphql
mutation {
  saveSystemFlowPlans(systemFlowPlansInput: {
    transformPlans: [...]
    dataSinkPlans: [...]
    restDataSources: [...]
    timedDataSources: [...]
    onErrorDataSources: [...]
  })
}
```

**Permission:** `FlowPlanCreate`

## Flow State Mutations

### Set Flow State

```graphql
mutation {
  setFlowState(flowType: TRANSFORM, flowName: "my-transform", flowState: RUNNING)
}
```

`FlowState` values: `STOPPED`, `RUNNING`, `PAUSED`

**Permission:** `FlowUpdate`

### Set Flow State by Tags

Change state for all flows matching a tag filter.

```graphql
# Preview which flows would be affected
query {
  setFlowStateByTagsDryRun(filter: { all: ["v2"] }, flowState: RUNNING) {
    transform { name }
    dataSink { name }
  }
}

# Apply the change
mutation {
  setFlowStateByTags(filter: { all: ["v2"] }, flowState: RUNNING) {
    transform { name }
    dataSink { name }
  }
}
```

**Permission:** `FlowUpdate`

## Data Source Configuration Mutations

### Max Errors

Set the maximum number of consecutive errors before a data source stops accepting data.

```graphql
mutation { setRestDataSourceMaxErrors(name: "my-source", maxErrors: 10) }
mutation { setTimedDataSourceMaxErrors(name: "my-timed", maxErrors: 5) }
mutation { setOnErrorDataSourceMaxErrors(name: "my-error", maxErrors: 3) }
```

**Permission:** `FlowUpdate`

### Rate Limiting

```graphql
mutation {
  setRestDataSourceRateLimit(name: "my-source", rateLimit: {
    unit: BYTES
    maxAmount: 1073741824
    durationSeconds: 3600
  })
}

mutation { removeRestDataSourceRateLimit(name: "my-source") }
```

**Permission:** `FlowUpdate`

### Timed Data Source Control

```graphql
# Set memo for tracking
mutation { setTimedDataSourceMemo(name: "my-timed", memo: "Processing batch 42") }

# Update cron schedule
mutation { setTimedDataSourceCronSchedule(name: "my-timed", cronSchedule: "0 */5 * * * *") }

# Trigger immediate execution
mutation { taskTimedDataSource(name: "my-timed", memo: "Manual trigger") }
```

**Permission:** `FlowUpdate`

### Expected Annotations

Set annotation keys that a data sink expects before egressing.

```graphql
mutation {
  setDataSinkExpectedAnnotations(flowName: "my-sink", expectedAnnotations: ["reviewed", "approved"])
}
```

**Permission:** `FlowUpdate`

## Test Mode Mutations

Enable or disable test mode for flows. Test mode marks all DeltaFiles processed by the flow for easy identification and cleanup.

```graphql
mutation { enableTransformTestMode(flowName: "my-transform") }
mutation { disableTransformTestMode(flowName: "my-transform") }
mutation { enableDataSinkTestMode(flowName: "my-sink") }
mutation { disableDataSinkTestMode(flowName: "my-sink") }
mutation { enableRestDataSourceTestMode(name: "my-source") }
mutation { disableRestDataSourceTestMode(name: "my-source") }
mutation { enableTimedDataSourceTestMode(name: "my-timed") }
mutation { disableTimedDataSourceTestMode(name: "my-timed") }
mutation { enableOnErrorDataSourceTestMode(name: "my-error") }
mutation { disableOnErrorDataSourceTestMode(name: "my-error") }
```

**Permission:** `FlowUpdate`

## Topic Queries

Topics are the publish-subscribe routing layer that connects data sources, transforms, and data sinks.

```graphql
# List all topics with publishers and subscribers
query {
  getAllTopics {
    name
    publishers { name type state condition }
    subscribers { name type state condition }
  }
}

# Get specific topics
query {
  getTopics(names: ["raw-data", "processed-data"]) {
    name
    publishers { name type }
    subscribers { name type }
  }
}

# Get a single topic
query {
  getTopic(name: "raw-data") {
    name
    publishers { name type state valid sourcePlugin { artifactId } }
    subscribers { name type state valid }
  }
}
```

**Permission:** `FlowView`
