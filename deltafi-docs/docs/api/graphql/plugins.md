---
# ABOUTME: Documents GraphQL queries and mutations for plugin management.
# ABOUTME: Covers plugin queries, installation, lifecycle operations, and variable management.
---

# Plugins GraphQL API

Queries and mutations for managing DeltaFi plugins — installable extensions that provide actions and flow plans.

## Queries

### List Plugins

```graphql
query {
  plugins {
    pluginCoordinates { groupId artifactId version }
    displayName
    description
    actionKitVersion
    installState
    installError
    installAttempts
    lastSuccessfulVersion
    canRollback
    disabled
    imageName
    imageTag
    actions {
      name
      type
      supportsJoin
      actionOptions { description }
      docsMarkdown
    }
    variables { name value dataType required masked }
    dependencies { groupId artifactId version }
  }
}
```

**Permission:** `PluginsView`

### Plugin Install Status

Get a summary of plugin installation states.

```graphql
query {
  pluginInstallStatus {
    installed
    pending
    installing
    failed
    removing
    failedPlugins {
      pluginCoordinates { artifactId version }
      installError
    }
  }
}
```

**Permission:** `PluginsView`

### Action Descriptors

Get descriptors for all registered actions across all plugins.

```graphql
query {
  actionDescriptors {
    name
    type
    supportsJoin
    schema
    actionOptions {
      description
      inputSpec { contentSummary contentSpecs { name mediaType description } }
      outputSpec { contentSummary passthrough }
      filters { description conditions }
      errors { description conditions }
      notes
    }
    docsMarkdown
  }
}
```

**Permission:** `PluginsView`

### Action Names by Family

Get action names grouped by family.

```graphql
query {
  getActionNamesByFamily {
    family
    actionNames
  }
}
```

**Permission:** `FlowView`

### Data Source Error State

Check which data sources have exceeded their maximum error threshold.

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

## Mutations

### Install Plugin

Install a plugin from a container image.

```graphql
mutation {
  installPlugin(
    image: "registry.example.com/my-plugin:1.0.0"
    imagePullSecret: "my-registry-secret"
  ) {
    success
    errors
    info
  }
}
```

**Permission:** `PluginInstall`

### Uninstall Plugin

```graphql
# Safe uninstall (fails if dependencies exist)
mutation {
  uninstallPlugin(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  }) {
    success errors
  }
}

# Force uninstall (ignores dependency checks)
mutation {
  forcePluginUninstall(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  }) {
    success errors
  }
}
```

**Permission:** `PluginUninstall`

### Plugin Lifecycle

```graphql
# Restart a plugin container
mutation { restartPlugin(pluginName: "my-plugin", waitForSuccess: true) }

# Retry a failed installation
mutation {
  retryPluginInstall(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  })
}

# Rollback to previous version
mutation {
  rollbackPlugin(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  })
}

# Disable plugin (stops container)
mutation {
  disablePlugin(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  })
}

# Enable plugin
mutation {
  enablePlugin(pluginCoordinates: {
    groupId: "com.example"
    artifactId: "my-plugin"
    version: "1.0.0"
  })
}
```

**Permission:** `PluginInstall` (restart, retry, rollback, disable, enable)

### Plugin Variables

```graphql
# Set all system plugin variables
mutation {
  savePluginVariables(variables: [
    { name: "apiKey", description: "API key", dataType: STRING, required: true, value: "abc123", masked: true }
  ])
}

# Add or update a single system plugin variable
mutation {
  saveSystemPluginVariable(variable: {
    name: "apiKey"
    description: "API key"
    dataType: STRING
    required: true
    value: "abc123"
    masked: true
  })
}

# Remove all system plugin variables
mutation { removePluginVariables }

# Remove a single system plugin variable
mutation { removeSystemPluginVariable(name: "apiKey") }

# Set variables for a specific plugin
mutation {
  setPluginVariableValues(
    pluginCoordinates: { groupId: "com.example", artifactId: "my-plugin", version: "1.0.0" }
    variables: [{ key: "endpoint", value: "https://api.example.com" }]
  )
}
```

**Permission:** `PluginVariableUpdate`
