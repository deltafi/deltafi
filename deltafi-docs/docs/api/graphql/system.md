---
# ABOUTME: Documents GraphQL queries and mutations for system configuration.
# ABOUTME: Covers properties, snapshots, SSL/TLS, SpEL validation, links, and lookup tables.
---

# System GraphQL API

Queries and mutations for system configuration — properties, snapshots, SSL/TLS management, SpEL expression validation, links, and lookup tables.

## Properties

### Queries

#### Get Property Sets

```graphql
query {
  getPropertySets(splitByGroup: true) {
    id
    displayName
    description
    properties {
      key
      description
      defaultValue
      value
      propertySource
      dataType
      refreshable
      editable
    }
  }
}
```

**Permission:** `SystemPropertiesRead`

#### Get DeltaFi Properties

Returns all system configuration properties as a typed object.

```graphql
query {
  getDeltaFiProperties {
    systemName
    ingressEnabled
    egressEnabled
    ageOffDays
    deleteFrequency
    inMemoryQueueSize
    maxFlowDepth
    requeueDuration
    autoResumeCheckFrequency
    metricsEnabled
    uiUseUTC
    topBarBackgroundColor
    securityBannerText
    securityBannerEnabled
    messageOfTheDay
  }
}
```

**Permission:** `SystemPropertiesRead`

### Mutations

#### Update Properties

```graphql
mutation {
  updateProperties(updates: [
    { key: "ageOffDays", value: "30" },
    { key: "inMemoryQueueSize", value: "10000" }
  ])
}
```

**Permission:** `SystemPropertiesUpdate`

#### Remove Property Overrides

Reset properties to their default values.

```graphql
mutation {
  removePropertyOverrides(propertyNames: ["ageOffDays", "inMemoryQueueSize"])
}
```

**Permission:** `SystemPropertiesUpdate`

## Links

Manage custom links displayed in the DeltaFi UI.

```graphql
# Save or update a link
mutation {
  saveLink(link: {
    name: "Grafana"
    url: "https://grafana.example.com"
    description: "Metrics dashboard"
    linkType: EXTERNAL
  }) {
    id name url linkType
  }
}

# Remove a link
mutation {
  removeLink(id: "550e8400-e29b-41d4-a716-446655440000")
}
```

Link types: `EXTERNAL` (opens in new tab), `DELTAFILE_LINK` (contextual link on DeltaFile pages)

**Permission:** `SystemPropertiesUpdate`

## System Snapshots

Snapshots capture the full system configuration for backup and restore.

### Queries

```graphql
# List all snapshots
query {
  getSystemSnapshots {
    id reason created schemaVersion
  }
}

# Get a specific snapshot
query {
  getSystemSnapshot(snapshotId: "550e8400-...") {
    id reason created schemaVersion snapshot
  }
}

# Paginated/filtered snapshots
query {
  getSystemSnapshotsByFilter(
    offset: 0
    limit: 10
    filter: { createdAfter: "2024-01-01T00:00:00Z" }
    sortField: CREATED
    direction: DESC
  ) {
    totalCount
    systemSnapshots { id reason created }
  }
}
```

**Permission:** `SnapshotRead`

### Mutations

```graphql
# Create a snapshot
mutation {
  snapshotSystem(reason: "Before upgrade") {
    id created
  }
}

# Restore from a snapshot
mutation {
  resetFromSnapshotWithId(snapshotId: "550e8400-...", hardReset: false) {
    success errors
  }
}

# Import a snapshot from external source
mutation {
  importSnapshot(snapshot: {
    id: "550e8400-..."
    reason: "Imported from production"
    created: "2024-01-15T10:00:00Z"
    schemaVersion: 1
    snapshot: {}
  }) {
    id created
  }
}

# Import and immediately apply
mutation {
  importSnapshotAndReset(snapshot: {...}, hardReset: false) {
    success errors
  }
}

# Delete a snapshot
mutation {
  deleteSnapshot(snapshotId: "550e8400-...") {
    success
  }
}
```

**Permissions:** `SnapshotCreate` (create), `SnapshotRevert` (reset), `SnapshotCreate` and `SnapshotRevert` (import and reset), `SnapshotDelete` (delete)

## SSL/TLS Management

### Queries

```graphql
# Get a specific key/cert pair
query {
  getKeyCert(secretName: "my-tls-secret") {
    secretName
    cert {
      commonName
      validFrom
      validTo
      daysUntilExpiration
      isExpired
      isValid
      issuer
      subjectAlternativeNames
      fingerprints { certificate publicKey }
    }
    usedBy
  }
}

# Get all SSL settings
query {
  sslSettings {
    keys {
      secretName
      cert { commonName isValid daysUntilExpiration }
      usedBy
    }
    caChain
  }
}
```

**Permission:** `SystemPropertiesRead`

### Mutations

```graphql
# Save a key/cert pair
mutation {
  saveKeyCert(
    secretName: "my-tls-secret"
    keyCertPair: {
      key: "-----BEGIN PRIVATE KEY-----\n..."
      cert: "-----BEGIN CERTIFICATE-----\n..."
      keyPassphrase: "optional-passphrase"
    }
  ) {
    secretName
    cert { commonName isValid }
  }
}

# Delete a key/cert pair
mutation {
  deleteKeyCert(secretName: "my-tls-secret") {
    secretName
  }
}

# Append to CA chain
mutation {
  appendToCaChain(certs: "-----BEGIN CERTIFICATE-----\n...")
}

# Replace entire CA chain
mutation {
  saveCaChain(certs: "-----BEGIN CERTIFICATE-----\n...")
}
```

**Permission:** `SystemPropertiesUpdate`

## SpEL Expression Validation

Test and validate [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) expressions used in publish-subscribe routing conditions.

```graphql
# Test an expression with sample data
mutation {
  testSpelExpression(
    expression: "metadata['type'] == 'important'"
    mode: CONDITION
    testData: {
      metadata: [{ key: "type", value: "important" }]
      content: [{ name: "file.txt", mediaType: "text/plain", size: 1024 }]
      deltaFileName: "test.txt"
    }
  ) {
    valid
    result
    errorMessage
    errorPosition
  }
}

# Validate expression syntax only
mutation {
  validateSpelExpression(expression: "metadata['key'] != null", mode: CONDITION) {
    valid
    errorMessage
    errorPosition
  }
}
```

Expression modes: `CONDITION` (evaluates to boolean), `TEMPLATE` (evaluates to string)

**Permission:** `FlowUpdate`

## Lookup Tables

Lookup tables provide key-value reference data that actions can query during processing. For background, see [Lookup Tables](/lookup-tables).

### Queries

```graphql
# List all lookup tables
query {
  getLookupTables {
    name
    columns
    keyColumns
    serviceBacked
    backingServiceActive
    totalRows
  }
}

# Get a specific table
query {
  getLookupTable(lookupTableName: "country-codes") {
    name
    columns
    keyColumns
    totalRows
    refreshDuration
    lastRefresh
  }
}

# Query table data
query {
  lookup(
    lookupTableName: "country-codes"
    matchingColumnValues: [{ column: "code", value: ["US", "GB"] }]
    resultColumns: ["code", "name"]
    sortColumn: "name"
    sortDirection: ASC
    offset: 0
    limit: 50
  ) {
    totalCount
    rows
  }
}
```

**Permission:** `SystemPropertiesRead`

### Mutations

```graphql
# Create a lookup table
mutation {
  createLookupTable(lookupTableInput: {
    name: "country-codes"
    columns: ["code", "name", "region"]
    keyColumns: ["code"]
    serviceBacked: false
    backingServiceActive: false
    pullThrough: false
  }) {
    success
  }
}

# Insert or update rows
mutation {
  upsertLookupTableRows(
    lookupTableName: "country-codes"
    rows: [
      [{ column: "code", value: "US" }, { column: "name", value: "United States" }, { column: "region", value: "Americas" }],
      [{ column: "code", value: "GB" }, { column: "name", value: "United Kingdom" }, { column: "region", value: "Europe" }]
    ]
  ) {
    success
  }
}

# Remove rows
mutation {
  removeLookupTableRows(
    lookupTableName: "country-codes"
    rows: [
      [{ column: "code", value: "US" }]
    ]
  ) {
    success
  }
}

# Toggle backing service
mutation {
  setLookupTableBackingServiceActive(lookupTableName: "country-codes", active: true) {
    success
  }
}

# Delete a table
mutation {
  deleteLookupTable(lookupTableName: "country-codes") {
    success
  }
}
```

**Permission:** `SystemPropertiesUpdate`

## Miscellaneous Queries

### Version

```graphql
query { version }
```

### Export Configuration

Export the entire system configuration as YAML.

```graphql
query { exportConfigAsYaml }
```

**Permission:** `SystemPropertiesRead`

### Stress Testing

Generate test data for load testing.

```graphql
# Generate test DeltaFiles
mutation {
  stressTest(
    flow: "test-source"
    contentSize: 1024
    numFiles: 1000
    batchSize: 100
    metadata: [{ key: "test", value: "true" }]
  )
}

# Generate test analytic events
mutation {
  stressTestAnalyticEvents(numRecords: 10000, hours: 24)
}
```

**Permission:** `StressTest`
