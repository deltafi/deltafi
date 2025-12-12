# Action Parameters

In DeltaFi, `Actions` are the building blocks of flows. Each action in the flow can be configured with a known set of parameters that can tailor the action behavior for the flow. These parameters can be set in three ways, with literal values, from plugin variables, or using templates to extract the value from the `ActionInput` being sent to the `Action`.

### Literal Value

This is a hard coded value used directly in the action parameter. This should be used when you have a parameter that has a fixed value in the flow.

Example - hard code the format parameter to `GZIP` in the Decompress action

```yaml
name: gzip-transform
type: TRANSFORM
description: Flow that expects gzipped data. Decompresses and publishes the data.
subscribe:
  - topic: gzipped-file
transformActions:
  - name: Decompress
    type: org.deltafi.core.action.compress.Decompress
    parameters:
      format: GZIP
publish:
  rules:
    - topic: decompressed-file
```

### Plugin Variable

Plugin variables are typed values that can be referenced in action parameters by name.  They allow operators to change an action parameter value at runtime as well as reuse a value across actions.  Action parameters should be set with a plugin variable when the value needs to be reconfigurable.  To use a plugin variable in your action parameter, you wrap the name of the variable in `${}`.

Example - use a plugin variable named `egressUrl` in url parameter of the HttpEgress action

```yaml
---
name: example-data-sink
type: DATA_SINK
description: An example data sink
subscribe:
  - topic: output-topic
egressAction:
  name: HttpPost
  type: org.deltafi.core.action.egress.HttpEgress
  parameters:
    url: ${egressUrl}
    method: POST
    metadataKey: deltafiMetadata
```

### Parameter Templating

Parameter templating pulls information out of the current `ActionInput` being sent to the `Action` into the action parameter. To use a template wrap the parameter value in `{{ }}`. Parameter templating should be used when parameters need to be adjusted dynamically based on the `ActionInput`.  

The following fields are available to use in a parameter template.

| Field | Description | 
|-------|-------------|
| `{{ deltaFileName }}` | The name of the DeltaFile |
| `{{ did }}` | The did of the DeltaFile |
| `{{ metadata }}` | The metadata from the first DeltaFileMessage |
| `{{ content }}` | The content list from the first DeltaFileMessage |
| `{{ actionContext }}` | The current ActionContext (see [Action Contex](actions#context) for information about the subfields) |
| `{{ deltaFileMessages }}` | The full list of DeltaFileMessages, useful for joins |
| `{{ now() }}` | Helper method to get the current timestamp |

Example - use templating to pull out various pieces of information about the DeltaFile being sent to an action

```yaml
---
name: params-to-content
type: TRANSFORM
description: Sample flow that extracts DeltaFile information into action parameters
subscribe:
- topic: params-to-content
transformActions:
- name: AddMetadata
  type: org.deltafi.core.action.metadata.ModifyMetadata
  parameters:
    addOrModifyMetadata:
      type: ${dataType}
- name: ParamsToContent
  type: org.deltafi.sample.actions.ParamsToContent
  parameters:
    contentParams:
      did: "{{ did }}"
      deltaFileName: "{{ deltaFileName }}"
      type: "{{ metadata['type'] }}"
      timestamp: "{{ now() }}"
      dataSource: "{{ actionContext.dataSource }}"
      processedBy: "{{ actionContext.flowName + '.' + actionContext.actionName }}"
      contentInfo:
        name: "{{ content[0].name }}"
        mediaType: "{{ content[0].mediaType }}"
        size: "{{ content[0].getSize() }}"
publish:
  rules:
  - topic: deltafile-param-content
```

#### Templating - Spring Expression Language (SPeL)

The templates used in action parameters can include SPeL expressions which are evaluated to a string value. This gives operators the ability to do things like call methods against the values or filter/search through content and metadata. The template can be embedded inside a literal value, for example - `https://api.service/{{ deltaFileName.toLowerCase() }}` would evaluate to `https://api.service/input.txt` when the DeltaFileName is `Input.txt`. Templates can also be used within the value of a plugin variable.

## Secure Parameters with EnvVar

::: warning Java Action Kit Only
The `EnvVar` type is currently only supported in the Java Action Kit. Python support is planned for a future release.
:::

The `EnvVar` type provides a secure way to reference sensitive values like passwords, API keys, and other credentials in action parameters. Instead of storing secrets directly in flow configurations (which are persisted and may be logged), you store the **name** of an environment variable that contains the secret. The actual secret value is resolved at runtime from the environment.

### Why Use EnvVar?

Storing secrets directly in action parameters creates security risks:
- Flow configurations are stored in the database and may be visible in logs
- Secrets can be exposed when flows are exported or shared
- There's no clear separation between configuration and secrets management

With `EnvVar`, only the environment variable name (e.g., `MY_API_KEY`) is stored in the flow configuration. The actual secret is:
- Stored securely in Kubernetes secrets or another secrets manager
- Injected into the plugin's environment at runtime
- Never persisted in DeltaFi's database or logs

### Defining an EnvVar Parameter

In your action's parameter class, use the `EnvVar` type for any field that should reference a secret:

```java
package org.deltafi.myaction;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.parameters.EnvVar;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyActionParameters extends ActionParameters {
    @JsonPropertyDescription("Environment variable containing the API key")
    private EnvVar apiKeyEnvVar = new EnvVar();

    @JsonPropertyDescription("Environment variable containing the password")
    private EnvVar passwordEnvVar = new EnvVar();
}
```

### Resolving EnvVar Values

In your action implementation, resolve the environment variable to get the actual secret value:

```java
@Override
public Result myAction(ActionContext context, MyActionParameters params, MyInput input) {
    // resolve() throws IllegalStateException if the env var is not set
    String apiKey = params.getApiKeyEnvVar().resolve();

    // resolveOrDefault() returns the default if the env var is not set
    String password = params.getPasswordEnvVar().resolveOrDefault(null);

    // isSet() checks if an env var name has been configured
    if (params.getPasswordEnvVar().isSet()) {
        // use the password
    }

    // ... use the resolved values
}
```

### Configuring EnvVar in Flows

When configuring a flow, specify just the environment variable name (in UPPER_SNAKE_CASE):

```yaml
name: my-secure-flow
type: TRANSFORM
transformActions:
  - name: CallSecureAPI
    type: org.deltafi.myaction.MyAction
    parameters:
      apiKeyEnvVar: MY_API_KEY
      passwordEnvVar: DB_PASSWORD
```

The UI provides a specialized editor for `EnvVar` fields that:
- Displays a warning reminding operators to enter the variable name, not the secret value
- Validates that names follow the UPPER_SNAKE_CASE convention
- Shows the `$` prefix to indicate this is an environment variable reference

### Setting Up Environment Variables

Environment variables must be available to the plugin at runtime. The method differs depending on whether you're running DeltaFi with Docker Compose or Kubernetes. For a comprehensive overview of site configuration, see [Site Configuration](/operating/site-configuration).

#### Docker Compose

For Compose deployments, inject environment variables into plugins using the `site/compose.yaml` file. This file is automatically created in your DeltaFi installation directory and merges with the base docker-compose configuration.

Add environment variables to a plugin by service name. For plugins installed via the UI, the service name matches the plugin's image name (e.g., `deltafi-stix` for the STIX plugin):

```yaml
# site/compose.yaml
services:
  deltafi-stix:
    environment:
      MY_API_KEY: "your-actual-api-key"
      DB_PASSWORD: "your-actual-password"
```

After modifying `site/compose.yaml`, run `deltafi up` to apply the changes.

::: tip
For sensitive values in Compose, the environment variables are stored in plain text in the compose file. If you need more secure secrets management, consider using Docker secrets or an external secrets manager.
:::

#### Kubernetes

For Kubernetes deployments, you'll need to create a custom Helm template that:
1. Creates a Secret containing your sensitive values
2. Patches the plugin's deployment to inject the secret as environment variables

Create a file in `site/templates/` (e.g., `site/templates/my-plugin-secrets.yaml`):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: my-plugin-secrets
type: Opaque
stringData:
  MY_API_KEY: "your-actual-api-key"
  DB_PASSWORD: "your-actual-password"
---
# Patch the plugin deployment to add env vars from the secret
# Note: The deployment name matches the plugin image name
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deltafi-stix
spec:
  template:
    spec:
      containers:
        - name: deltafi-stix
          envFrom:
            - secretRef:
                name: my-plugin-secrets
```

After creating the template, run `deltafi up` to apply the changes.

::: tip
For production environments, consider using external secrets management solutions like HashiCorp Vault, AWS Secrets Manager, or Kubernetes External Secrets Operator to avoid storing secrets directly in your configuration files.
:::

### Backward Compatibility Pattern

When migrating an existing action to use `EnvVar`, you can support both the old direct value and the new environment variable reference:

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class MyActionParameters extends ActionParameters {
    @JsonPropertyDescription("Environment variable containing the password")
    private EnvVar passwordEnvVar = new EnvVar();

    @Deprecated
    @JsonPropertyDescription("The password (DEPRECATED: use passwordEnvVar instead)")
    private String password;

    /**
     * Gets the password from the environment variable or falls back to the deprecated field.
     * @throws IllegalStateException if passwordEnvVar is configured but the env var is not set
     */
    public String resolvePassword() {
        if (passwordEnvVar.isSet()) {
            return passwordEnvVar.resolve();  // Throws if env var not found
        }
        return password;  // Fall back to deprecated field only if EnvVar not configured
    }
}
```

This allows existing flows using the deprecated `password` field to continue working while encouraging migration to the more secure `passwordEnvVar` approach. Note that if `passwordEnvVar` is configured but the environment variable doesn't exist, an error is thrown rather than silently falling back to the deprecated field.