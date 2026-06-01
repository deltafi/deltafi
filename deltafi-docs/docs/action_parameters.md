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

#### Helper Methods

In addition to the fields above, the following helper methods are available inside `{{ }}` templates. All helpers are null-safe: passing a missing metadata value (e.g. `metadata['absent']`) returns `null` rather than throwing. Helpers may be composed (e.g. `{{ sha256(base64Decode(metadata['signed'])) }}`).

**Time & identity**

| Method | Returns | Description |
|--------|---------|-------------|
| `now()` | String | Current timestamp in ISO-8601 format |
| `timestamp()` | String | Current time as epoch milliseconds |
| `uuid()` | String | A new random (v4) UUID |

**Encoding / decoding**

| Method | Returns | Description |
|--------|---------|-------------|
| `base64Encode(value)` | String | Standard Base64 encoding of the UTF-8 bytes |
| `base64Decode(value)` | String | Decode standard Base64 to a UTF-8 string |
| `base64UrlEncode(value)` | String | URL-safe Base64 encoding, no padding |
| `base64UrlDecode(value)` | String | Decode URL-safe Base64 to a UTF-8 string |
| `hexEncode(value)` | String | Lowercase hex encoding of the UTF-8 bytes |
| `hexDecode(value)` | String | Decode hex to a UTF-8 string (errors on invalid hex) |
| `urlEncode(value)` | String | Percent-encode for inclusion in a URL |
| `urlDecode(value)` | String | Decode a percent-encoded URL string |

**Hashing (hex output)**

| Method | Returns | Description |
|--------|---------|-------------|
| `sha256(value)` | String | Lowercase hex SHA-256 digest |
| `sha512(value)` | String | Lowercase hex SHA-512 digest |
| `sha1(value)` | String | Lowercase hex SHA-1 digest |
| `md5(value)` | String | Lowercase hex MD5 digest |

**Escaping**

| Method | Returns | Description |
|--------|---------|-------------|
| `escapeJson(value)` | String | Escape characters for safe embedding in JSON string literals |
| `escapeXml(value)` | String | Escape characters for safe embedding in XML/HTML content |
| `escapeHtml(value)` | String | Escape characters for safe embedding in HTML |

**String fallbacks**

| Method | Returns | Description |
|--------|---------|-------------|
| `defaultIfBlank(value, fallback)` | String | Returns `value` if it is non-blank, otherwise `fallback` |
| `coalesce(v1, v2, ...)` | String | Returns the first non-blank argument, or `null` if all are blank |

Example - hash a customer id, base64-encode a metadata value, fall back through a chain of optional keys

```yaml
---
name: hash-and-encode
type: TRANSFORM
description: Demonstrates the SpEL helper methods
subscribe:
  - topic: incoming
transformActions:
  - name: TagMetadata
    type: org.deltafi.core.action.metadata.ModifyMetadata
    parameters:
      addOrModifyMetadata:
        customer_hash: "{{ sha256(metadata['customer_id']) }}"
        url_safe_name: "{{ urlEncode(metadata['display_name']) }}"
        decoded_token: "{{ base64Decode(metadata['signed_token']) }}"
        tracking_id:   "{{ uuid() }}"
        category:      "{{ coalesce(metadata['category'], metadata['legacy_category'], 'uncategorized') }}"
        json_payload:  "{{ escapeJson(metadata['user_message']) }}"
publish:
  rules:
    - topic: tagged
```

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

## Nested Parameter Structures

Action parameters can contain nested objects and lists to organize complex configurations. Use `@JsonPropertyDescription` on nested class fields to provide descriptions that appear in the UI.

### Defining Nested Parameters (Java)

```java
package org.deltafi.myaction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyActionParameters extends ActionParameters {
    @JsonPropertyDescription("Primary endpoint configuration")
    private EndpointConfig primaryEndpoint;

    @JsonPropertyDescription("Optional fallback endpoints to try if primary fails")
    private List<EndpointConfig> fallbackEndpoints;

    @Data
    public static class EndpointConfig {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The URL to connect to")
        private String url;

        @JsonPropertyDescription("Connection timeout in milliseconds")
        private int timeoutMs = 30000;

        @JsonPropertyDescription("Authentication settings for this endpoint")
        private AuthConfig auth;
    }

    @Data
    public static class AuthConfig {
        @JsonPropertyDescription("Authentication type: NONE, BASIC, or BEARER")
        private String type = "NONE";

        @JsonPropertyDescription("Username for BASIC auth")
        private String username;

        @JsonPropertyDescription("Environment variable containing the password or token")
        private EnvVar credential;
    }
}
```

### Defining Nested Parameters (Python)

```python
from pydantic import BaseModel, Field
from typing import List, Optional

class AuthConfig(BaseModel):
    type: str = Field(default="NONE", description="Authentication type: NONE, BASIC, or BEARER")
    username: Optional[str] = Field(default=None, description="Username for BASIC auth")
    password: Optional[str] = Field(default=None, description="Password or token")

class EndpointConfig(BaseModel):
    url: str = Field(description="The URL to connect to")
    timeout_ms: int = Field(default=30000, description="Connection timeout in milliseconds")
    auth: Optional[AuthConfig] = Field(default=None, description="Authentication settings for this endpoint")

class MyActionParameters(BaseModel):
    primary_endpoint: EndpointConfig = Field(description="Primary endpoint configuration")
    fallback_endpoints: List[EndpointConfig] = Field(default=[], description="Optional fallback endpoints to try if primary fails")
```

### Defining Nested Parameters (Go)

```go
package actions

import actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"

type MyActionParams struct {
    PrimaryEndpoint   EndpointConfig   `json:"primaryEndpoint" description:"Primary endpoint configuration" required:"true"`
    FallbackEndpoints []EndpointConfig `json:"fallbackEndpoints" description:"Optional fallback endpoints to try if primary fails"`
}

type EndpointConfig struct {
    URL       string      `json:"url" description:"The URL to connect to" required:"true"`
    TimeoutMs int         `json:"timeoutMs" description:"Connection timeout in milliseconds" default:"30000"`
    Auth      *AuthConfig `json:"auth" description:"Authentication settings for this endpoint"`
}

type AuthConfig struct {
    Type       string           `json:"type" description:"Authentication type: NONE, BASIC, or BEARER" default:"NONE"`
    Username   string           `json:"username" description:"Username for BASIC auth"`
    Credential actionkit.EnvVar `json:"credential" description:"Environment variable containing the password or token"`
}
```

### Defining Nested Parameters (C++)

```cpp
#include <deltafi/plugin>

struct AuthConfig {
    std::string type = "NONE";
    std::string username;
    deltafi::EnvVar credential;

    DELTAFI_PARAMS(AuthConfig,
        FIELD(type, "Authentication type: NONE, BASIC, or BEARER"),
        FIELD(username, "Username for BASIC auth"),
        FIELD(credential, "Environment variable containing the password or token"))
};

struct EndpointConfig {
    std::string url;
    int timeout_ms = 30000;
    std::optional<AuthConfig> auth;

    DELTAFI_PARAMS(EndpointConfig,
        FIELD(url, "The URL to connect to"),
        FIELD(timeout_ms, "Connection timeout in milliseconds"),
        FIELD(auth, "Authentication settings for this endpoint"))
};

struct MyActionParams {
    EndpointConfig primary_endpoint;
    std::vector<EndpointConfig> fallback_endpoints;

    DELTAFI_PARAMS(MyActionParams,
        FIELD(primary_endpoint, "Primary endpoint configuration"),
        FIELD(fallback_endpoints, "Optional fallback endpoints to try if primary fails"))
};
```

### Configuring Nested Parameters in Flows

```yaml
name: my-flow
type: TRANSFORM
transformActions:
  - name: CallAPI
    type: org.deltafi.myaction.MyAction
    parameters:
      primaryEndpoint:
        url: https://api.example.com/v1
        timeoutMs: 5000
        auth:
          type: BASIC
          username: myuser
          credential: API_PASSWORD
      fallbackEndpoints:
        - url: https://backup.example.com/v1
          timeoutMs: 10000
```

The UI displays nested parameters with expandable sections. Each field shows its label and description (via the info icon) as defined in `@JsonPropertyDescription`.

## Secure Parameters with EnvVar

::: warning Java, Go, and C++ Action Kits Only
The `EnvVar` type is currently supported in the Java, Go, and C++ Action Kits. Python support is planned for a future release.
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

### Defining and Resolving EnvVar in Go

In Go, use the `actionkit.EnvVar` type in your parameter struct:

```go
type MyActionParams struct {
    APIKeyEnvVar    actionkit.EnvVar `json:"apiKeyEnvVar" description:"Environment variable containing the API key"`
    PasswordEnvVar  actionkit.EnvVar `json:"passwordEnvVar" description:"Environment variable containing the password"`
}
```

The `EnvVar` type resolves the environment variable value at runtime:

```go
func (a *MyAction) Transform(df *actionkit.Deltafile, params MyActionParams) *actionkit.Deltafile {
    // Resolve() returns the value of the named environment variable
    apiKey, err := params.APIKeyEnvVar.Resolve()
    if err != nil {
        return df.Errorf("Failed to resolve API key").SetErrorContext(err.Error())
    }

    // ResolveOrDefault() returns the default if the env var is not set
    password := params.PasswordEnvVar.ResolveOrDefault("")

    // IsSet() checks if an env var name has been configured
    if params.PasswordEnvVar.IsSet() {
        // use the password
    }

    // ... use the resolved values
}
```

### Defining and Resolving EnvVar in C++

In C++, use the `deltafi::EnvVar` type in your parameter struct:

```cpp
struct MyActionParams {
    deltafi::EnvVar api_key_env_var;
    deltafi::EnvVar password_env_var;

    DELTAFI_PARAMS(MyActionParams,
        FIELD(api_key_env_var, "Environment variable containing the API key"),
        FIELD(password_env_var, "Environment variable containing the password"))
};
```

The `EnvVar` type resolves the environment variable value at runtime:

```cpp
// resolve() throws if the env var is not set
auto api_key = params.api_key_env_var.resolve();

// resolve_or_default() returns the default if the env var is not set
auto password = params.password_env_var.resolve_or_default("");

// is_set() checks if an env var name has been configured
if (params.password_env_var.is_set()) {
    // use the password
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

Environment variables referenced by `EnvVar` parameters must be available to the plugin at runtime. See [Plugin Environment Variables](operating/site-configuration.md#plugin-environment-variables) for how to configure `deltafi.pluginEnv` in your site values.

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