# Transform Action

## Description

A Transform Action transforms the content it receives. It may also update metadata and add annotations.

## Java

### Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `TransformInput` providing the content and metadata to be transformed

### Transform Input

```java
public class TransformInput {
    List<ActionContent> content;
    Map<String, String> metadata;
}
```

### Return Types

The `transform` method must return a `TransformResultType`, which is implemented by `TransformResult`, `TransformResults`,
`ErrorResult`, and `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.
The `TransformResults` contains a list of `ChildTransformResult`. Each `ChildTransformResult` creates a new child
DeltaFile that continues through the same flow.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldTransformAction extends TransformAction<Parameters> {
    public HelloWorldTransformAction() {
        super("Add some content noting that we did a really good job");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull Parameters params, @NotNull TransformInput input) {
        if (context.getDid().toString().startsWith("2")) {
            return new FilterResult(context, "We prefer dids that do not start with 2");
        }
        
        String data = input.content(0).loadString() + "\nHelloWorldTransformAction did a great job";

        TransformResult result = new TransformResult(context);
        result.addMetadata("transformKey", "transformValue");
        result.addAnnotation("transformAnnotation", "value");
        result.saveContent(data, "transform-named-me", "text/plain");
        return result;
    }
}
```

## Python

### Interface

A TransformAction must implement the `transform` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `TransformInput` providing the content and metadata to be transformed

### Transform Input

```python
class TransformInput(NamedTuple):
    content: List[Content]
    metadata: dict
```

### Return Types

The `transform()` method must return one of: `TransformResult`, `TransformResults`, `ErrorResult`, or `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.
The `TransformResults` contains a list of `ChildTransformResult`. Each `ChildTransformResult` creates a new child
DeltaFile that continues through the same flow.

### Example

```python
from deltafi.action import TransformAction
from deltafi.domain import Context
from deltafi.input import TransformInput
from deltafi.result import FilterResult, TransformResult
from pydantic import BaseModel


class HelloWorldTransformAction(TransformAction):
    def __init__(self):
        super().__init__('Add some content noting that we did a really good job')

    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        context.logger.info(f"Transforming {context.did}")
        if context.did.startswith('2'):
            return FilterResult(context, 'We prefer dids that do not start with 2')

        data = f"{transform_input.content[0].load_str()}\nHelloWorldTransformAction did a great job"

        return TransformResult(context)
                .save_string_content(data, 'transform-named-me', 'text/plain')
                .add_metadata('transformKey', 'transformValue')
                .annotate('transformAnnotation', 'value')
```

## Go

::: warning Beta
The Go action kit is currently in beta. Interfaces may change in future releases.
:::

### Interface

A TransformAction must implement the `Transform` method which receives:
* `*Deltafile` providing the input content, metadata, and context fields
* A typed params struct containing flow parameters specified for the action

The action returns the same `*Deltafile`, which accumulates output content, metadata changes, and annotations.

### Deltafile

The `Deltafile` is the primary type action authors interact with. It carries context fields (DID, Name, FlowName, etc.),
provides access to input content and metadata, and accumulates output content and metadata changes.

```go
// Context fields (read-only)
df.Did            // DeltaFile ID
df.Name           // original filename
df.DataSource     // data source name
df.FlowName       // flow name
df.ActionName     // action name

// Input content
df.FirstContent()           // *ActionContent, error
df.ContentAt(index)         // *ActionContent, error
df.ContentNamed(name)       // *ActionContent, error
df.GetContentList()        // []ActionContent
df.HasContent()             // bool

// Output content
df.SaveStringContent(name, mediaType, data)   // *ActionContent, error
df.SaveBytesContent(name, mediaType, data)    // *ActionContent, error
df.AddContent(content)                         // *Deltafile (chainable)
df.PassthroughContent()                        // *Deltafile (chainable)

// Child DeltaFiles
df.AddChild(childName)        // *Deltafile (chainable)

// Metadata
df.GetMetadata(key)                  // string, error
df.GetMetadataOrDefault(key, def)    // string
df.AddMetadata("key", "value")       // *Deltafile (chainable)
df.DeleteMetadataKeys("key1")        // *Deltafile (chainable)

// Annotations
df.AddAnnotation("key", "value")     // *Deltafile (chainable)

// Error/Filter signaling
df.Errorf("format", args...)         // *Deltafile (chainable)
df.Filterf("format", args...)        // *Deltafile (chainable)
```

### Return Value

The `Transform` method always returns a `*Deltafile`. The kit inspects the returned Deltafile to determine the outcome:

* `Errorf` was called — the DeltaFile is errored.
* `Filterf` was called — the DeltaFile is filtered.
* One or more children were added via `AddChild` — each child becomes a new DeltaFile that continues through the flow.
* Otherwise, the action succeeds, and any saved content, metadata changes, and annotations are applied to the DeltaFile.

### File layout

Each Go action lives in two files inside the plugin's `actions/` directory:

* `<name>_action.go` — the action struct, `init()` registration, and the action method (`Transform`, `Egress`, etc.)
* `<name>_action_params.go` — the typed `*Params` struct only

Splitting params into a sibling file keeps the action code focused on logic and gives the schema round-trip tooling (described below) a deterministic location to read from and write to.

### Parameter struct tags

The kit reads the following struct tags when generating JSON Schema:

| Tag | Effect |
|---|---|
| `json:"name"` | parameter name displayed in the DeltaFi UI |
| `description:"..."` | human-readable description |
| `default:"..."` | default value (string form; coerced to the field type) |
| `enum:"a,b,c"` | allowed values (renders as a dropdown) |
| `required:"true"` | marks the parameter as required |
| `minimum:"0"` / `maximum:"100"` | numeric bounds |

Supported field types: Go primitives (`bool`, `int`, `string`, `float64`), slices, string-keyed maps, pointers to other structs declared in the same params file, and the kit's `actionkit.DataSize` and `actionkit.EnvVar` helpers.

### Schema round-trip tooling

The kit ships a `cmd/actionkit-schemagen` binary, invoked from every plugin's Makefile:

```sh
make jsonFromParams   # writes actions/<name>_action_params.json from each _params.go
make paramsFromJson   # writes actions/<name>_action_params.go   from each _params.json
```

`jsonFromParams` produces schemas that are byte-for-byte identical to what the kit sends to core during plugin registration (the AST tool reuses the same `actionkit.GenerateSchema` pathway). `paramsFromJson` regenerates the Go source from a schema; the output is canonical (fields alphabetize, comments drop, slice-of-struct fields come back as slice-of-pointer-to-struct) but functionally equivalent.

### Example

`actions/hello_world_go_transform_action.go`:

```go
package actions

import (
    "strings"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
    actionkit.RegisterTransform("HelloWorldGoTransformAction", &HelloWorldGoTransformAction{}).
        WithParams(HelloWorldGoTransformParams{}).
        Describe("Add some content noting that we did a really good job")
}

type HelloWorldGoTransformAction struct{}

func (a *HelloWorldGoTransformAction) Transform(df *actionkit.Deltafile, params HelloWorldGoTransformParams) *actionkit.Deltafile {
    if strings.HasPrefix(df.Did, "2") {
        return df.Filterf("We prefer dids that do not start with 2")
    }

    content, err := df.FirstContent()
    if err != nil {
        return df.Errorf("Failed to load content").SetErrorContext(err.Error())
    }

    data, err := content.LoadString()
    if err != nil {
        return df.Errorf("Failed to load content").SetErrorContext(err.Error())
    }

    transformed := data + "\nHelloWorldGoTransformAction did a great job"

    if _, err := df.SaveStringContent("transform-named-me", "text/plain", transformed); err != nil {
        return df.Errorf("Failed to save content").SetErrorContext(err.Error())
    }

    df.AddMetadata("transformKey", "transformValue")
    df.AddAnnotation("transformAnnotation", "value")
    return df
}
```

`actions/hello_world_go_transform_action_params.go`:

```go
package actions

// HelloWorldGoTransformParams defines the action's configurable parameters.
type HelloWorldGoTransformParams struct {
    // Add fields with json + description/default tags to expose them in the UI.
}
```

## C++

::: warning Beta
The C++ action kit is currently in beta. Interfaces may change in future releases.
:::

### Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext&` describing the action's environment and current execution
* `TransformInput&` providing the content and metadata to be transformed

Actions with typed parameters receive an additional `Params&` argument between context and input.

### Transform Input

```cpp
struct TransformInput {
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;
};
```

### Return Types

The `transform` method must return a `TransformResultType`, which is a `std::variant` of `TransformResult`, `TransformResults` (multi-child), `FilterResult`, and `ErrorResult`.

### Example

```cpp
#pragma once
#include <deltafi/plugin>

class HelloWorldCppTransformAction {
public:
    deltafi::TransformResultType transform(deltafi::ActionContext& context,
                                            deltafi::TransformInput& input) {
        if (context.did.starts_with("2")) {
            return deltafi::FilterResult(context, "We prefer dids that do not start with 2");
        }

        auto data = input.first_content().load_string()
            + "\nHelloWorldCppTransformAction did a great job";

        deltafi::TransformResult result(context);
        result.save_content(data, "transform-named-me", "text/plain");
        result.add_metadata("transformKey", "transformValue");
        result.add_annotation("transformAnnotation", "value");
        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppTransformAction,
    "HelloWorldCppTransformAction",
    "Add some content noting that we did a really good job")
```
