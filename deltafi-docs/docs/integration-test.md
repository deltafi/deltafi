# Integration Testing

Integration tests validate complete data flows in DeltaFi by providing test inputs and expected outputs. Unlike unit tests that verify individual actions in isolation, integration tests verify that data sources, transforms, and data sinks work together correctly.

## Overview

Integration tests are defined in YAML configuration files that specify:
- Which flows to start
- What data to ingest
- What the expected output should be

When a test runs, DeltaFi:
1. Starts the required flows
2. Ingests the test data
3. Waits for processing to complete
4. Compares actual results against expected results

## Configuration Format

Integration test configurations are YAML or JSON files with the following structure:

```yaml
name: my-plugin-test
description: Tests the transform flow with sample input
timeout: PT5M
plugins:
  - groupId: org.myorg
    artifactId: my-plugin
    version: ANY
dataSources:
  - my-rest-data-source
transformationFlows:
  - my-transform
dataSinks:
  - my-egress
inputs:
  - dataSource: my-rest-data-source
    ingressFileName: test-input.json
    contentType: application/json
    base64Encoded: false
    data: |
      {"key": "value"}
expectedDeltaFiles:
  - stage: COMPLETE
    expectedFlows:
      - flow: my-transform
        type: TRANSFORM
        actions: [MyTransformAction]
```

```json
{
  "name": "my-plugin-test",
  "description": "Tests the transform flow with sample input",
  "timeout": "PT5M",
  "plugins": [
    {
      "groupId": "org.myorg",
      "artifactId": "my-plugin",
      "version": "ANY"
    }
  ],
  "dataSources": [
    "my-rest-data-source"
  ],
  "transformationFlows": [
    "my-transform"
  ],
  "dataSinks": [
    "my-egress"
  ],
  "inputs": [
    {
      "dataSource": "my-rest-data-source",
      "ingressFileName": "test-input.json",
      "contentType": "application/json",
      "base64Encoded": false,
      "data": "{\"key\": \"value\"}\n"
    }
  ],
  "expectedDeltaFiles": [
    {
      "stage": "COMPLETE",
      "expectedFlows": [
        {
          "flow": "my-transform",
          "type": "TRANSFORM",
          "actions": [
            "MyTransformAction"
          ]
        }
      ]
    }
  ]
}
```

Examples in the remainder of this document will be shown in the YAML format, but all  structures are supported in JSON, too.

### Top-Level Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier for the test. Convention: `<plugin>.<test-name>` |
| `description` | No | Human-readable description of what the test validates |
| `timeout` | No | ISO 8601 duration for test timeout (default: `PT2M` = 2 minutes) |
| `plugins` | Yes | List of plugins required for the test |
| `dataSources` | Yes | Data source flows to start |
| `transformationFlows` | No | Transform flows to start |
| `dataSinks` | No | Data sink flows to start |
| `inputs` | Yes | Test data to ingest |
| `expectedDeltaFiles` | Yes | Expected results after processing |

### Plugin Coordinates

Specify required plugins using coordinates:

```yaml
plugins:
  - groupId: org.myorg
    artifactId: my-plugin
    version: ANY  # or specific version like "1.0.0"
```

Use `version: ANY` to match any installed version, useful when testing against development builds.

### Inputs

Each input defines data to ingest into a data source:

```yaml
inputs:
  - dataSource: my-rest-data-source
    ingressFileName: test-file.txt
    contentType: text/plain
    base64Encoded: false
    data: "Plain text content"
    metadata:
      - key: sourceId
        value: test-001
```

| Field | Required | Description |
|-------|----------|-------------|
| `dataSource` | Yes | Name of the data source to ingest into |
| `ingressFileName` | Yes | Filename for the ingested content |
| `contentType` | No | MIME type of the content |
| `base64Encoded` | No | Set `true` if `data` is base64 encoded (for binary content) |
| `data` | Yes | The content to ingest (can be empty string) |
| `metadata` | No | Key-value pairs to attach as metadata |

::: tip Backward Compatibility
The deprecated `flow` field is still accepted for backward compatibility, but `dataSource` is preferred.
:::

### Expected DeltaFiles

Define expected results hierarchically, matching DeltaFi's parent-child structure:

```yaml
expectedDeltaFiles:
  - stage: COMPLETE
    childCount: 2
    expectedFlows:
      - flow: my-transform
        type: TRANSFORM
        state: COMPLETE
        actions: [TransformAction1, TransformAction2]
        metadata:
          containsKeys: [sourceId]
          keyValueMatchers:
            - name: status
              value: processed
              exact: true
    children:
      - stage: COMPLETE
        parentCount: 1
        expectedFlows:
          - flow: my-egress
            type: DATA_SINK
            actions: [MyEgressAction]
```

#### ExpectedDeltaFile Fields

| Field | Required | Description |
|-------|----------|-------------|
| `stage` | Yes | Expected stage: `COMPLETE`, `ERROR`, or `CANCELLED` |
| `childCount` | No | Expected number of child DeltaFiles (defaults to `children` list size) |
| `parentCount` | No | Expected number of parent DeltaFiles |
| `expectedFlows` | No | List of flows that should have processed this DeltaFile |
| `children` | No | Nested `ExpectedDeltaFile` definitions for child DeltaFiles |
| `expectedContent` | No | Validate actual egressed or transformed content (see ExpectedContentList) |
| `annotations` | No | Expected annotations on the DeltaFile (see KeyValueChecks) |

#### ExpectedFlow Fields

| Field | Required | Description |
|-------|----------|-------------|
| `flow` | Yes | Name of the flow |
| `type` | Yes | Flow type: `DATA_SOURCE`, `TRANSFORM`, or `DATA_SINK` |
| `state` | No | Expected flow state (defaults to `COMPLETE`) |
| `actions` | No | List of action names that should have executed |
| `metadata` | No | Metadata checks (see KeyValueChecks) |

#### KeyValueChecks (for metadata and annotations)

```yaml
metadata:
  containsKeys:
    - requiredKey1
    - requiredKey2
  keyValueMatchers:
    - name: exactMatch
      value: expected-value
      exact: true
    - name: patternMatch
      value: "order-\\d+"
      exact: false  # uses regex matching
```

| Field | Description |
|-------|-------------|
| `containsKeys` | List of keys that must be present |
| `keyValueMatchers` | List of key-value comparisons |
| `keyValueMatchers[].name` | Key name to check |
| `keyValueMatchers[].value` | Expected value (exact or regex pattern) |
| `keyValueMatchers[].exact` | `true` for exact match, `false` for regex (default: `false`) |

#### ExpectedContentList (validate egressed or transformed data)

Verify the actual content produced by an egress or transform action:

```yaml
expectedContent:
  flow: my-egress
  type: DATA_SINK
  action: MyEgressAction
  data:
    - name: output.json
      mediaType: application/json
      value: |
        {"result": "success"}
```

| Field | Required | Description |
|-------|----------|-------------|
| `flow` | Yes | Flow name where content was produced |
| `type` | Yes | Flow type (usually `DATA_SINK`) |
| `action` | Yes | Action name that produced the content |
| `data` | Yes | List of expected content items (see ExpectedContentData) |

#### ExpectedContentData Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Content name (filename) |
| `mediaType` | No | Expected MIME type |
| `value` | Conditional | Expected content (required if `contains` not specified) |
| `contains` | Conditional | List of strings that must appear in content |
| `base64Encoded` | No | Set `true` if `value` is base64 encoded |
| `ignoreWhitespace` | No | Remove whitespace before comparison |
| `macroSubstitutions` | No | Replace `{{DID}}` and `{{PARENT_DID}}` with actual values |
| `extraSubstitutions` | No | Additional regex replacements on actual content |

Either `value` (exact match) or `contains` (substring checks) must be specified, but not both.

## Shipping Integration Tests with Your Plugin

### Recommended Directory Structure

Place integration tests in your plugin's `src/main/resources` directory:

```
my-plugin/
├── src/main/java/...
├── src/main/resources/
│   ├── flows/
│   │   └── my-flow.json
│   └── integration-tests/
│       ├── basic-test.yaml
│       └── edge-case-test.yaml
└── build.gradle
```

### Loading Tests After Plugin Installation

Integration tests which are part of the plugin's source tree are automatically loaded when the plugin
registers with DeltaFi. Loading additional integration tests or modifying existing ones may also be
done using the CLI:

```bash
# Load a single test
deltafi integration-test load src/main/resources/integration-tests/basic-test.yaml

# Load all tests from a directory
deltafi integration-test load src/main/resources/integration-tests/*.yaml
```

## Running Integration Tests

### Run All Tests

```bash
deltafi integration-test run
```

### Run Specific Tests

```bash
deltafi integration-test run my-plugin.basic-test my-plugin.edge-case-test
```

### Run Tests by Pattern

```bash
# Run all tests containing "my-plugin" in the name
deltafi integration-test run --like "my-plugin"

# Run tests matching multiple patterns (OR logic)
deltafi integration-test run --like "transform" --like "egress"
```

### Set Custom Timeout

To change the amount of time the CLI wil wait for all tests to finish, use the `--timeout` option. Note: This does not change the actual integration test configuration timeout.

```bash
deltafi integration-test run --timeout 10m
```

### View Results

```bash
# Show summary of all test results
deltafi integration-test summary

# Output as JSON for automation
deltafi integration-test summary --json
```

## Complete Example

Here's a complete integration test that validates a transform flow:

```yaml
name: my-plugin.json-transform-test
description: Validates JSON transformation with metadata enrichment
timeout: PT3M

plugins:
  - groupId: org.myorg
    artifactId: my-plugin
    version: ANY
  - groupId: org.deltafi
    artifactId: deltafi-core-actions
    version: ANY

dataSources:
  - json-rest-data-source

transformationFlows:
  - json-transform

dataSinks:
  - json-egress

inputs:
  - dataSource: json-rest-data-source
    ingressFileName: input.json
    contentType: application/json
    base64Encoded: false
    data: |
      {
        "orderId": "12345",
        "customer": "Acme Corp"
      }
    metadata:
      - key: source
        value: api
      - key: priority
        value: high

expectedDeltaFiles:
  - stage: COMPLETE
    childCount: 0
    expectedFlows:
      - flow: json-transform
        type: TRANSFORM
        actions: [JsonEnricher, JsonValidator]
        metadata:
          containsKeys: [source, priority]
          keyValueMatchers:
            - name: status
              value: validated
              exact: true
      - flow: json-egress
        type: DATA_SINK
        actions: [JsonEgressAction]
    expectedContent:
      flow: json-egress
      type: DATA_SINK
      action: JsonEgressAction
      data:
        - name: output.json
          mediaType: application/json
          contains:
            - "orderId"
            - "12345"
            - "enriched"
```

## Binary Content Example

For binary content, use base64 encoding:

```yaml
inputs:
  - dataSource: binary-data-source
    ingressFileName: image.png
    contentType: image/png
    base64Encoded: true
    data: iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==
```

## Testing Child DeltaFiles

When transforms split content into multiple children:

```yaml
expectedDeltaFiles:
  - stage: COMPLETE
    childCount: 3
    expectedFlows:
      - flow: split-transform
        type: TRANSFORM
        actions: [SplitContent]
    children:
      - stage: COMPLETE
        parentCount: 1
        expectedFlows:
          - flow: process-transform
            type: TRANSFORM
      - stage: COMPLETE
        parentCount: 1
        expectedFlows:
          - flow: process-transform
            type: TRANSFORM
      - stage: COMPLETE
        parentCount: 1
        expectedFlows:
          - flow: process-transform
            type: TRANSFORM
```

## Troubleshooting

### Test Fails with "Flow not found"

Ensure all flows listed in `dataSources`, `transformationFlows`, and `dataSinks` exist and are properly configured. Integration tests start existing flows—they do not create them.

### Test Times Out

- Increase the `timeout` value
- Check that all required plugins are installed
- Verify flows are in RUNNING state before running tests

### Content Mismatch

Use `ignoreWhitespace: true` if whitespace differences cause failures:

```yaml
expectedContent:
  data:
    - name: output.txt
      ignoreWhitespace: true
      value: expected content
```

### Dynamic Values in Output

Use macro substitutions for values that change per test run:

```yaml
expectedContent:
  data:
    - name: output.json
      macroSubstitutions: true
      value: |
        {"did": "{{DID}}"}
```

Use `extraSubstitutions` to normalize timestamps or UUIDs:

```yaml
expectedContent:
  data:
    - name: output.json
      extraSubstitutions:
        - key: "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
          value: "UUID"
      value: |
        {"id": "UUID"}
```

## CLI Reference

| Command | Description |
|---------|-------------|
| `deltafi integration-test list` | List all loaded tests |
| `deltafi integration-test load <file>` | Load test from YAML file |
| `deltafi integration-test run [names...]` | Run tests |
| `deltafi integration-test run --like <pattern>` | Run tests matching pattern |
| `deltafi integration-test summary` | Show test results |
| `deltafi integration-test remove <names...>` | Remove tests |
| `deltafi integration-test clear` | Remove all test results |

For detailed CLI options, see the [TUI documentation](/operating/TUI#integration-test).
