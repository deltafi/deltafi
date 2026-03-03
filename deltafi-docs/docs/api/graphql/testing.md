---
# ABOUTME: Documents GraphQL queries and mutations for integration test management.
# ABOUTME: Covers test configuration, execution, result retrieval, and expected outcome types.
---

# Testing GraphQL API

Queries and mutations for managing integration tests. Integration tests verify end-to-end data processing by ingesting test data and validating the resulting DeltaFile state. For background, see [Integration Testing](/integration-test).

## Queries

### Get Integration Tests

```graphql
# List all integration tests
query {
  getIntegrationTests {
    name
    description
    plugins { groupId artifactId version }
    dataSources
    transformationFlows
    dataSinks
    timeout
  }
}

# Get a specific test
query {
  getIntegrationTest(name: "my-test") {
    name
    description
    inputs {
      dataSource
      contentType
      ingressFileName
      base64Encoded
      data
      metadata { key value }
    }
    expectedDeltaFiles {
      stage
      childCount
      expectedFlows {
        flow
        type
        state
        actions
        metadata { containsKeys keyValueMatchers { name value exact } }
      }
      expectedContent {
        flow
        action
        data { name mediaType contains value }
      }
      annotations { containsKeys }
    }
    timeout
  }
}
```

**Permission:** `IntegrationTestView`

### Get Test Results

```graphql
# List all test results
query {
  getTestResults {
    id
    testName
    status
    start
    stop
    errors
  }
}

# Get a specific test result
query {
  getTestResult(id: "result-123") {
    id
    testName
    status
    start
    stop
    errors
  }
}
```

Test status values: `INVALID`, `STARTED`, `SUCCESSFUL`, `FAILED`

**Permission:** `IntegrationTestView`

## Mutations

### Load Integration Test from YAML

Load an integration test defined in YAML format.

```graphql
mutation {
  loadIntegrationTest(configYaml: """
    name: my-test
    description: Tests the basic transform pipeline
    dataSources:
      - my-source
    transformationFlows:
      - my-transform
    dataSinks:
      - my-sink
    inputs:
      - dataSource: my-source
        contentType: text/plain
        ingressFileName: test.txt
        data: Hello World
    timeout: PT60S
    expectedDeltaFiles:
      - stage: COMPLETE
        expectedFlows:
          - flow: my-transform
            type: TRANSFORM
            state: COMPLETE
  """) {
    success
    errors
  }
}
```

**Permission:** `IntegrationTestUpdate`

### Save Integration Test

Save an integration test using the GraphQL input type.

```graphql
mutation {
  saveIntegrationTest(testCase: {
    name: "my-test"
    description: "Tests the basic pipeline"
    dataSources: ["my-source"]
    transformationFlows: ["my-transform"]
    dataSinks: ["my-sink"]
    inputs: [{
      dataSource: "my-source"
      contentType: "text/plain"
      ingressFileName: "test.txt"
      data: "Hello World"
    }]
    timeout: "PT60S"
    expectedDeltaFiles: [{
      stage: COMPLETE
      expectedFlows: [{
        flow: "my-transform"
        type: TRANSFORM
        state: COMPLETE
      }]
    }]
  }) {
    success errors
  }
}
```

**Permission:** `IntegrationTestUpdate`

### Start Integration Test

Execute a saved integration test.

```graphql
mutation {
  startIntegrationTest(name: "my-test") {
    id
    testName
    status
    start
    errors
  }
}
```

**Permission:** `IntegrationTestUpdate`

### Remove Integration Test

```graphql
mutation {
  removeIntegrationTest(name: "my-test")
}
```

**Permission:** `IntegrationTestDelete`

### Remove Test Result

```graphql
mutation {
  removeTestResult(id: "result-123")
}
```

**Permission:** `IntegrationTestDelete`

## Test Configuration Types

### TestCaseIngress

Defines input data for a test.

| Field | Type | Description |
|-------|------|-------------|
| `dataSource` | `String` | Data source to ingest into |
| `contentType` | `String` | MIME type of the test data |
| `ingressFileName` | `String` | Filename for the ingressed data |
| `base64Encoded` | `Boolean` | Whether `data` is base64-encoded |
| `data` | `String` | The test data content |
| `metadata` | `[KeyValue]` | Metadata key-value pairs |

### ExpectedDeltaFile

Defines expected outcome for a DeltaFile.

| Field | Type | Description |
|-------|------|-------------|
| `stage` | `DeltaFileStage` | Expected lifecycle stage |
| `childCount` | `Int` | Expected number of child DeltaFiles |
| `parentCount` | `Int` | Expected number of parent DeltaFiles |
| `expectedFlows` | `[ExpectedFlow]` | Expected flow states |
| `expectedContent` | `ExpectedContentList` | Expected content data |
| `annotations` | `KeyValueChecks` | Expected annotations |
| `children` | `[ExpectedDeltaFile]` | Expected child DeltaFile structure (nested up to 5 levels) |

### ExpectedFlow

| Field | Type | Description |
|-------|------|-------------|
| `flow` | `String` | Flow name |
| `type` | `FlowType` | Flow type |
| `state` | `DeltaFileFlowState` | Expected flow state |
| `actions` | `[String]` | Expected action names |
| `metadata` | `KeyValueChecks` | Expected metadata checks |

### ExpectedContentData

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Content name |
| `mediaType` | `String` | Expected media type |
| `contains` | `[String]` | Strings the content must contain |
| `value` | `String` | Exact expected content value |
| `base64Encoded` | `Boolean` | Whether `value` is base64-encoded |
| `ignoreWhitespace` | `Boolean` | Ignore whitespace in comparison |
| `macroSubstitutions` | `Boolean` | Enable macro substitution in expected values |
| `extraSubstitutions` | `[KeyValue]` | Additional macro substitution variables |

### KeyValueChecks

| Field | Type | Description |
|-------|------|-------------|
| `containsKeys` | `[String]` | Keys that must be present |
| `keyValueMatchers` | `[KeyValueMatcher]` | Key-value pairs that must match |
