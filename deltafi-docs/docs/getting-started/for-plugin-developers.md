# Plugin Developer's Guide

This guide is for developers who want to build custom DeltaFi plugins with their own actions.

## Overview

DeltaFi plugins are containerized applications that provide custom actions for data transformation. You can write plugins in **Java** or **Python**.

## Prerequisites

- Docker Desktop with at least 4 cores and 8GB RAM allocated
- Git
- For Java plugins: JDK 21+, Gradle
- For Python plugins: (optional) Python 3.13+, uv

## Setup

### 1. Install DeltaFi

Follow the [Quick Start](/getting-started/quick-start) to install DeltaFi. This installs the **DeltaFi TUI** (Terminal User Interface) - a command-line tool that manages the entire DeltaFi system. See [Understanding the TUI](/operating/TUI#understanding-the-tui) for how it works, or read on for the basics.

When the installation wizard asks about your role, select **Plugin Development**. When asked about orchestration mode, select **Compose** (recommended for most users).

This will set up a DeltaFi environment using Docker Compose.

### 2. Verify the Installation

After setup completes:

```bash
deltafi status
```

The DeltaFi UI will be available at [http://local.deltafi.org](http://local.deltafi.org).

## Common Scenarios

### Creating a New Plugin

Use the plugin generator to create a new plugin in the `repos/` directory:

```bash
# Generate a Java plugin
deltafi plugin generate --java my-plugin

# Generate a Python plugin
deltafi plugin generate --python my-plugin

# Interactive mode (shows diffs before applying)
deltafi plugin generate --java my-plugin -i
```

This creates a complete plugin project in `$DELTAFI_INSTALL_DIR/repos/my-plugin/` with:
- Build configuration (Gradle or Poetry)
- Sample action
- Docker build files
- Flow definitions

To build and deploy your new plugin:

```bash
cd repos/my-plugin
./gradlew install    # Builds, pushes image, and installs to DeltaFi
```

### Working on an Existing Plugin

To work on an existing plugin, clone it into the `repos/` directory:

```bash
cd $DELTAFI_INSTALL_DIR/repos
git clone <plugin-repo-url> my-existing-plugin
cd my-existing-plugin
./gradlew install    # Builds, pushes image, and installs to DeltaFi
```

The `./gradlew install` task handles:
1. Building the plugin code
2. Creating the Docker image
3. Pushing the image (to KinD registry or Docker)
4. Installing the plugin to DeltaFi

### Iterating on Changes

After making code changes:

```bash
./gradlew install    # Rebuild and redeploy
```

The plugin will automatically restart with your changes.

## Plugin Structure

### Java Plugin

```
my-plugin/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/
│   └── com/example/myplugin/
│       ├── MyPluginApplication.java
│       └── actions/
│           └── MyTransformAction.java
└── src/main/resources/
    └── flows/
        └── my-transform.yaml
```

### Python Plugin

```
my-plugin/
├── pyproject.toml
├── Dockerfile
├── my_plugin/
│   ├── __init__.py
│   └── actions/
│       └── my_transform_action.py
└── flows/
    └── my-transform.yaml
```

## Writing Actions

### Action Types

| Type | Purpose | Java Class | Python Class |
|------|---------|------------|--------------|
| Transform | Modify content/metadata | `TransformAction<P>` | `TransformAction` |
| Timed Ingress | Pull data on schedule | `TimedIngressAction<P>` | `TimedIngressAction` |
| Egress | Send data to external systems | `EgressAction<P>` | `EgressAction` |

### Java Transform Action Example

```java
public class MyTransformAction extends TransformAction<MyParameters> {

    public MyTransformAction() {
        super(ActionOptions.builder()
            .description("Transforms input data")
            .build());
    }

    @Override
    public TransformResultType transform(
            ActionContext context,
            MyParameters params,
            TransformInput input) {

        // Read input content
        String content = input.content(0).loadString();

        // Transform it
        String transformed = content.toUpperCase();

        // Return result
        TransformResult result = new TransformResult(context);
        result.saveContent(transformed, "output.txt", "text/plain");
        return result;
    }
}
```

### Python Transform Action Example

```python
from deltafi.action import TransformAction
from deltafi.domain import Context, TransformInput
from deltafi.result import TransformResult

class MyTransformAction(TransformAction):
    def __init__(self):
        super().__init__("Transforms input data")

    def transform(self, context: Context, params: dict,
                  input: TransformInput) -> TransformResult:
        # Read input content
        content = input.content[0].load_string()

        # Transform it
        transformed = content.upper()

        # Return result
        result = TransformResult(context)
        result.save_string_content(transformed, "output.txt", "text/plain")
        return result
```

## Defining Flows

Flows wire your actions together. Create YAML files in `src/main/resources/flows/` (Java) or `flows/` (Python):

```yaml
name: my-transform
type: TRANSFORM
description: My custom transformation flow

subscribe:
  - topic: my-input

transformActions:
  - name: MyTransformAction
    type: com.example.myplugin.actions.MyTransformAction
    parameters:
      someParam: "${SOME_VARIABLE}"

publish:
  defaultRule:
    defaultBehavior: PUBLISH
    topic: my-output
```

## Testing

### Unit Testing (Java)

```java
public class MyTransformActionTest {
    @Test
    void transforms() {
        MyTransformAction action = new MyTransformAction();
        DeltaFiTestRunner runner = DeltaFiTestRunner.setup("test");

        ActionContent content = runner.saveContent("hello", "input.txt", "text/plain");
        TransformInput input = TransformInput.builder()
            .content(List.of(content))
            .build();

        TransformResultType result = action.transform(
            runner.actionContext(),
            new MyParameters(),
            input
        );

        TransformResultAssert.assertThat(result)
            .hasContentMatching(c -> c.loadString().equals("HELLO"));
    }
}
```

### Integration Testing

```bash
# Run all integration tests
deltafi integration-test run

# Run tests matching a pattern
deltafi integration-test run --like "my-plugin"
```

## Reference

- [Creating a Plugin](/plugins) - Detailed plugin structure
- [Actions](/actions) - Complete action API reference
- [Action Parameters](/action_parameters) - Parameter types including secure EnvVar references
- [Transform Actions](/actions/transform) - Transform action details
- [Egress Actions](/actions/egress) - Egress action details
- [Timed Ingress Actions](/actions/timed_ingress) - Ingress action details
- [Action Unit Testing](/unit-test) - Testing patterns
- [Simple Plugin Tutorial](/getting-started/simple-plugin) - Step-by-step walkthrough
