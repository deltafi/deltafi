---
# ABOUTME: Documents GraphQL queries and mutations for resume and delete policies.
# ABOUTME: Covers automatic error retry policies and data retention deletion policies.
---

# Policies GraphQL API

Queries and mutations for managing resume policies (automatic error retry) and delete policies (data retention).

## Resume Policies

Resume policies automatically retry DeltaFiles that encounter errors, based on configurable criteria. For background, see [Automatic Resume](/advanced/auto_resume).

### Queries

#### List All Resume Policies

```graphql
query {
  getAllResumePolicies {
    id
    name
    errorSubstring
    dataSource
    action
    maxAttempts
    priority
    backOff {
      delay
      maxDelay
      multiplier
      random
    }
  }
}
```

**Permission:** `ResumePolicyRead`

#### Get Single Resume Policy

```graphql
query {
  getResumePolicy(id: "550e8400-e29b-41d4-a716-446655440000") {
    id
    name
    errorSubstring
    maxAttempts
    backOff { delay multiplier }
  }
}
```

**Permission:** `ResumePolicyRead`

#### Dry Run

Preview whether a resume policy configuration is valid.

```graphql
query {
  resumePolicyDryRun(resumePolicyInput: {
    name: "retry-timeouts"
    errorSubstring: "Connection timeout"
    maxAttempts: 3
    backOff: { delay: 5, multiplier: 2 }
  }) {
    success
    errors
  }
}
```

**Permission:** `ResumePolicyDryRun`

### Mutations

#### Apply Resume Policies

Trigger resume policies to run against current errors.

```graphql
mutation {
  applyResumePolicies(names: ["retry-timeouts", "retry-parse-errors"]) {
    success
    info
    errors
  }
}
```

**Permission:** `ResumePolicyApply`

#### Load Resume Policies

Bulk load resume policies, optionally replacing all existing ones.

```graphql
mutation {
  loadResumePolicies(
    replaceAll: false
    policies: [
      {
        name: "retry-timeouts"
        errorSubstring: "Connection timeout"
        maxAttempts: 3
        backOff: { delay: 5, multiplier: 2, maxDelay: 60 }
      },
      {
        name: "retry-parse-errors"
        errorSubstring: "Parse error"
        action: "org.example.MyTransform"
        maxAttempts: 2
        priority: 10
        backOff: { delay: 10 }
      }
    ]
  ) {
    success errors
  }
}
```

**Permission:** `ResumePolicyCreate`

#### Update Resume Policy

```graphql
mutation {
  updateResumePolicy(resumePolicy: {
    id: "550e8400-e29b-41d4-a716-446655440000"
    name: "retry-timeouts"
    errorSubstring: "timeout"
    maxAttempts: 5
    backOff: { delay: 10, multiplier: 2, maxDelay: 120, random: true }
  }) {
    success errors
  }
}
```

**Permission:** `ResumePolicyUpdate`

#### Remove Resume Policy

```graphql
mutation {
  removeResumePolicy(id: "550e8400-e29b-41d4-a716-446655440000")
}
```

**Permission:** `ResumePolicyDelete`

### Resume Policy Fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String!` | Policy name |
| `errorSubstring` | `String` | Error message substring to match |
| `dataSource` | `String` | Limit to a specific data source |
| `action` | `String` | Limit to a specific action |
| `maxAttempts` | `Int!` | Maximum retry attempts |
| `priority` | `Int` | Priority when multiple policies match (higher = first) |
| `backOff.delay` | `Int!` | Initial delay in seconds |
| `backOff.maxDelay` | `Int` | Maximum delay in seconds |
| `backOff.multiplier` | `Int` | Delay multiplier for exponential backoff |
| `backOff.random` | `Boolean` | Add randomization to delay |

## Delete Policies

Delete policies control automatic data retention by removing DeltaFiles based on age or other criteria. For background, see [Data Retention](/advanced/data_retention).

### Queries

#### List Delete Policies

```graphql
query {
  getDeletePolicies {
    id
    name
    enabled
    flow
    ... on TimedDeletePolicy {
      afterCreate
      afterComplete
      minBytes
      deleteMetadata
    }
  }
}
```

**Permission:** `DeletePolicyRead`

#### Export Delete Policies

Export all delete policies in a format suitable for re-import.

```graphql
query {
  exportDeletePolicies {
    timedPolicies {
      id
      name
      enabled
      flow
      afterCreate
      afterComplete
      minBytes
      deleteMetadata
    }
  }
}
```

**Permission:** `DeletePolicyRead`

### Mutations

#### Load Delete Policies

Bulk load delete policies, optionally replacing all existing ones.

```graphql
mutation {
  loadDeletePolicies(
    replaceAll: false
    policies: {
      timedPolicies: [
        {
          name: "30-day-retention"
          enabled: true
          afterComplete: "P30D"
          deleteMetadata: false
        },
        {
          name: "error-cleanup"
          enabled: true
          flow: "test-flow"
          afterCreate: "P7D"
          deleteMetadata: true
        }
      ]
    }
  ) {
    success errors
  }
}
```

**Permission:** `DeletePolicyCreate`

#### Update Timed Delete Policy

```graphql
mutation {
  updateTimedDeletePolicy(policyUpdate: {
    id: "550e8400-e29b-41d4-a716-446655440000"
    name: "30-day-retention"
    enabled: true
    afterComplete: "P30D"
    minBytes: 1073741824
    deleteMetadata: false
  }) {
    success errors
  }
}
```

**Permission:** `DeletePolicyUpdate`

#### Enable/Disable Policy

```graphql
mutation {
  enablePolicy(id: "550e8400-e29b-41d4-a716-446655440000", enabled: false)
}
```

**Permission:** `DeletePolicyUpdate`

#### Remove Delete Policy

```graphql
mutation {
  removeDeletePolicy(id: "550e8400-e29b-41d4-a716-446655440000")
}
```

**Permission:** `DeletePolicyDelete`

### Delete Policy Fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String!` | Policy name |
| `enabled` | `Boolean!` | Whether the policy is active |
| `flow` | `String` | Limit to a specific flow (null = all flows) |
| `afterCreate` | `String` | Delete after this duration from creation (ISO-8601, e.g., `"P30D"`) |
| `afterComplete` | `String` | Delete after this duration from completion |
| `minBytes` | `Long` | Only delete if content exceeds this size |
| `deleteMetadata` | `Boolean!` | Also delete metadata (not just content) |
