# Getting Started Developing a Simple Plugin

Source code for the final result of this example can be found here:  https://gitlab.com/deltafi/plugins/example-plugin

## Prerequisites

You will need a Docker Compose system running on your local development system.  You can [follow the quick-start instructions](/getting-started/quick-start) to get a DeltaFi up and running.

For Java plugin development, you will need an IDE (VSCode or IntelliJ will work great) and you will need a Java 21 JDK installed.  

## Configure DeltaFi for Plugin Development

Run the DeltaFi TUI to reconfigure your system for plugin development: 

```bash
deltafi config --compose --plugin-development
```

When prompted to start DeltaFi, enter Y (yes) and press Enter.

```
Orchestration mode: Compose
Deployment mode:    PluginDevelopment

DeltaFi configuration has changed. Would you like to start DeltaFi? [y/N]: y
```

Your system will be configured to execute plugins locally using docker compose.

```bash
# See the DeltaFi system status
deltafi status
```

```bash
# See the current versions of subsystems and plugins running in your DeltaFi instance
deltafi versions
```

## Creating a Skeleton Plugin

DeltaFi provides a command-line interface for generating sample plugins
and actions in Java or Python. In this section some examples are used. For a
full list of options see the [plugin generate command](/operating/TUI.html#plugin).

### Create the Plugin

This creates a Java plugin. No actions are created by this, but a single
REST data source is made. The final argument is the plugin name. In this
example, the plugin name is "example-plugin".

```bash
deltafi plugin generate --java example-plugin
```

Plugins are generated in the `development.repoPath` directory. Check
`~/.deltafi/config.yaml` on your system for the exact location.

Using the `deltafi plugin generate` command above, the following defaults are used:
- group id: `org.deltafi.example.plugin`
- plugin name: `example-plugin`
- description: `Java plugin for DeltaFi: example-plugin`

To change these values, edit the `build.gradle` under the `example-plugin`
directory in your "repoPath".

### Add a Transform Action

This creates a sample TRANSFORM action and a single Transform flow.
In this example, the action name is JsonToYamlAction.

```bash
deltafi plugin generate action example-plugin JsonToYamlAction
```

### Add an Egress Action

This creates a sample EGRESS action and a single Data Sink.
In this example, the action name is SimpleEgressAction.
For this exercise, no changes to the EGRESS action or data sink flow will be made.

```bash
deltafi plugin generate action example-plugin --type EgressAction SimpleEgressAction
```

## Building and Installing Your Plugin

The generated plugin is ready to build and install (although it does not do much yet). This gradle task will rebuild the plugin and install it on your running DeltaFi instance.

```bash
# from the example-plugin directory
./gradlew install
```

If you make changes to your plugin, you may re-run `./gradlew install` to update the plugin with your changes.

If you want to compile and execute tests for your plugin, you can do so from the `example-plugin` directory:
```bash
./gradlew build
```

## Trying Out the New Plugin

Flows are versioned and packaged as part of your plugin source code. In a Java project they are located in `src/main/resources/flows`, in a Python project they are located in `src/flows/`.
Flows can reference both actions local to your plugin and any other actions that are running on your DeltaFi instance.
The skeleton plugin that was just generated contains a minimal complete flow with a minimal transform action that simply
passes data through unchanged while adding a sample annotation and sample
metadata key/value pair. At this point, the plugin is complete and can be built and installed (see previous
section "Building and Installing Your Plugin").

- If you navigate to the [DeltaFi user interface plugins page](http://local.deltafi.org/config/plugins), you can see that 
the plugin was installed.
- The plugin instantiates a REST Data Source that can be enabled on the [data sources page](http://local.deltafi.org/config/data-sources). Look for
  the REST Data Source named `example-plugin-data-source` and enable it.
- When the plugin generator created transform and egress actions, a Transform flow and a Data Sink, respectively, were also
created. See the [transforms page](http://local.deltafi.org/config/transforms) and [data sinks page](http://local.deltafi.org/config/data-sinks).
They will have `example-plugin` in their names, and should also be enabled.
- Now you can navigate to the [upload page](http://local.deltafi.org/deltafile/upload/) and upload a sample file.  Make 
  sure to select the `example-plugin-data-source` when you upload data.

The default implementation of the transform simply passes data through, which is not very interesting.  Now is the time to remedy that.

## Adding Some Logic to Implement the JsonToYamlAction Transformation

By default, the generated `example-plugin/src/main/java/org/deltafi/example/plugin/actions/JsonToYamlAction.java` reads 
the content that was ingressed and rewrites the content without modification. In this section 
we will be adding logic to convert Json data to Yaml and store the new content.

The first step is to add a Json ObjectMapper and Yaml ObjectMapper to the class:

```java
private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
```

Next we will replace the TODO with our logic to convert the data.  This implementation will
lower-case all the top level keys and reformat the results as YAML.
We will also modify the result to save and write the YAML output
instead rewriting the original content. This all needs to be wrapped in a try/catch to 
handle any IOExceptions thrown while mapping the data. When an exception occurs, 
we will return an `ErrorResult` that can be used to easily debug the exception from 
the [Errors page](http://local.deltafi.org/errors). To demonstrate annotations, the
number of keys from the input JSON format is recorded.

```java
log.info("Transforming {}", context.getDid());

TransformResult result = new TransformResult(context);
ActionContent actionContent = input.content(0);
byte[] content = actionContent.loadBytes();

try {
    Map<String, String> data = JSON_MAPPER.readValue(content, Map.class);
    Map<String, String> lowerCaseKeys = new HashMap<>();
    for (Map.Entry<String, String> entry : data.entrySet()) {
        lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
    }

    byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

    result.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
    result.addAnnotation("numKeys", Integer.toString(lowerCaseKeys.size()));
} catch (IOException e) {
    return new ErrorResult(context, "Failed to convert or store data", e);
}

return result;
``` 

Change the description of the action class to be more meaningful:
```java
public JsonToYamlAction() {
    super("Convert JSON to YAML");
}
```

After the modifications your class should look like:
```java
package org.deltafi.example.plugin.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JsonToYamlAction extends TransformAction<ActionParameters> {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public JsonToYamlAction() {
        super("Convert JSON to YAML");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ActionParameters params,
                                         @NotNull TransformInput input) {
        log.info("Transforming {}", context.getDid());

        TransformResult result = new TransformResult(context);
        ActionContent actionContent = input.content(0);
        byte[] content = actionContent.loadBytes();

        try {
            Map<String, String> data = JSON_MAPPER.readValue(content, Map.class);
            Map<String, String> lowerCaseKeys = new HashMap<>();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

            result.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
            result.addAnnotation("numKeys", Integer.toString(lowerCaseKeys.size()));
        } catch (IOException e) {
            return new ErrorResult(context, "Failed to convert or store data", e);
        }

        return result;
    }
}
```

Our implementation introduced a dependency on `jackson-dataformat-yaml`.  Add the needed 
dependency to `example-plugin/build.gradle`:

```gradle
plugins {
    id "com.github.hierynomus.license" version "${hierynomusLicenseVersion}"
    id 'org.deltafi.plugin-convention' version "${deltafiVersion}"
    id 'org.deltafi.test-summary' version "1.0"
}

group 'org.deltafi.example.plugin'

ext.pluginDescription = 'Java plugin for DeltaFi: example-plugin'

dependencies {
    // Added post 'deltafi plugin generate'
    // Needed for com.fasterxml.jackson.dataformat.yaml.YAMLFactory
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml'
}

license {
    header(rootProject.file('HEADER'))
    excludes(["**/*.xml", "**/generated/**/*.java", "**/*.MockMaker", "**/*.jks", "**/*.p12", "**/*.yaml", "**/*.tar",
              "**/*.gz", "**/*.Z", "**/*.zip", "**/*.xz", "**/*.ar", "**/*.txt", "**/*.xml", "**/*.html", "**/*.json",
              "**/test/resources/**", "**/node_modules/**"])
    strictCheck true
    mapping('java', 'SLASHSTAR_STYLE')
}
```

Your IDE may show an error for `import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;` until you fix `build.gradle`.

## Testing Your Plugin

Since we changed the behaviour of the action, some small changes to the unit test in `testTransform()` are necessary. Make the following updates to that method:

`src/test/java/org/deltafi/example/plugin/actions/JsonToYamlActionTest.java`
```java
@Test
void testTransform() {
    String json = """
            {
            "key1": "this",
            "key2": "that"
            }""";
    TransformResultAssert.assertThat(runTest("1efdd3c6-0c7b-11ef-a7c1-ff66faa1c348", json))
            .hasContentCount(1)
            .addedAnnotation("numKeys", "2");
}
```

Now that your plugin has some new logic, you can rebuild and deploy your new plugin version. The `build` task will insure that tests are executed, and the `install` task will install your plugin on the local DeltaFi system.

```
./gradlew build install
```

Generate some test data for your plugin:

`example-plugin/src/test/resources/test1.json`
```json
{
  "THING1": "This is thing 1",
  "Thing2": "This is thing 2",
  "thinG3": "This is thing 3"
}
```

`example-plugin/src/test/resources/test2.json`
```json
{
  "THING1": 1,
  "Thing2": 2,
  "thinG3": 3
}
```

`example-plugin/src/test/resources/test3.json`
```json
{
  "THIS": true,
  "That": 2,
  "thinGs": [
    {
      "name": "Thing 3",
      "DESCRIPTION": "This is thing 3"
    },
    {
      "name": "Thing 4",
      "DESCRIPTION": "This is thing 4"
    }
  ]
}
```

Once the plugin is installed, you can enable the flows with a DeltaFI TUI commands:

```bash
# Graph the end to end path, noting that all the flows are stopped
deltafi graph example-plugin-data-source
# Turn on all the flows for our example-plugin
deltafi data-source start example-plugin-data-source --all-actions
# Graph and verify that all the flows are now enabled
deltafi graph example-plugin-data-source
```

To run data through the flow you can go to the [upload page](http://local.deltafi.org/deltafile/upload/), choose your data source and upload a file, or you can upload via the TUI (as we will do here).

You can see the results of all uploaded DeltaFiles in the [search page](Http://local.deltafi.org/deltafile/search?ingressFlow=example-ingress), or in the TUI search tool (`deltafi search`)

Now we can ingress our test data:
```
deltafi ingress -d example-plugin-data-source -w src/test/resources/test1.json src/test/resources/test2.json src/test/resources/test3.json
```
When `test1.json` is uploaded, the file should complete and be egressed. However `test2.json` and `test3.json` result in errors based on our initial implementation.

The following changes to the transform action should fix the problem
(change the two map declarations from `Map<String, String>`
to `Map<String, Object>`):

`example-plugin/src/main/java/org/deltafi/example/plugin/actions/JsonToYamlAction.java`:
```java
package org.deltafi.example.plugin.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JsonToYamlAction extends TransformAction<ActionParameters> {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public JsonToYamlAction() {
        super("Convert JSON to YAML");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ActionParameters params,
                                         @NotNull TransformInput input) {
        log.info("Transforming {}", context.getDid());

        TransformResult result = new TransformResult(context);
        ActionContent actionContent = input.content(0);
        byte[] content = actionContent.loadBytes();

        try {
            Map<String, Object> data = JSON_MAPPER.readValue(content, Map.class);
            Map<String, Object> lowerCaseKeys = new HashMap<>();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

            result.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
            result.addAnnotation("numKeys", Integer.toString(lowerCaseKeys.size()));
        } catch (IOException e) {
            return new ErrorResult(context, "Failed to convert or store data", e);
        }

        return result;
    }
}
```

After making this code change, rebuild and reinstall the plugin:

```bash
./gradlew install
```

Now you can go to the [errors page](http://local.deltafi.org/errors) in the DeltaFi UI and resume the errored flows.  They should continue without error and egress well-formed YAML versions of the normalized input.

## Adding Another Flow to Your Plugin
New flows can be created under the `flows` directory. Any code changes or flow changes will require the docker image to be rebuilt via the `./gradlew install` command.

## Next Steps

- [Plugin Developer's Guide](/getting-started/for-plugin-developers) - Reference for common patterns and scenarios
- [Actions Reference](/actions) - Complete action API documentation
- [Action Unit Testing](/unit-test) - Testing patterns for your actions
