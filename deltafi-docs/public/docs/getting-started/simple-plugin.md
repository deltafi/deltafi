# Getting Started Developing a Simple Plugin

Source code for the final result of this example can be found here:  https://gitlab.com/deltafi/plugins/example-plugin

## Prerequisites

Ensure that your system meets the [prerequisites](/kind#prerequisites) for installing and running a development instance of DeltaFi in a self-contained KinD (Kubernetes in Docker) cluster.

For development, it is recommended that an IDE like IntelliJ or Visual Studio Code is installed for writing plugin code.  The IDE will be presumed and not covered in this tutorial.

## Installing the Development Environment

To execute a singlestep install of the latest released version of DeltaFi in a self-contained KinD cluster:

```bash
curl -fsSL https://gitlab.com/deltafi/installer/-/raw/main/kind-install.sh > kind-install.sh
chmod +x kind-install.sh
./kind-install.sh --dev
```

If you have previously done a demo install, you can simply execute the development bootstrap as follows:

```bash
deltafi/bootstrap-dev.sh
```

The UI can be accessed at `http://local.deltafi.org` and the Grafana metrics dashboard can be accessed at `http://metrics.local.deltafi.org/dashboards`.  You should visit those links in your browser to verify that the installation process is complete.

You can execute the following commands to see status from the command line:

```bash
# See status of the DeltaFi subsystems running in the local Kubernetes cluster
kubectl get pods
```

```bash
# See the DeltaFi system check status
deltafi status
```

```bash
# See the current versions of subsystems and plugins running in your DeltaFi instance
deltafi versions
```

## Creating a Skeleton Plugin
A new plugin can be initialized using the `deltafi plugin-init` command. This will prompt for the information necessary to create the plugin. Alternatively, you can initialize a new plugin by passing a configuration file to the command: `deltafi plugin-init -f plugin-config.json`.

Below are the steps to generate the [example-project](https://gitlab.com/deltafi/example-plugin). This must be run in the parent directory of the `deltafi` directory that was created by the installer (your location after running the singlestep install process).

Create `plugin-config.json`:
```json
{
  "artifactId": "example-plugin",
  "groupId": "org.deltafi.example",
  "description": "A plugin that takes in json and outputs yaml",
  "pluginLanguage": "JAVA",
  "actions": [
    {
      "className": "JsonToYamlAction",
      "description": "Converts arbitrary json to yaml",
      "actionType": "TRANSFORM"
    }
  ]
}
```

Generate the skeleton plugin with the following command:
```bash
deltafi plugin-init -f plugin-config.json
```
## Building and Installing Your Plugin

DeltaFi has a development CLI command called `cluster` which we will use for this example.

```bash
# Register the example plugin with cluster tool
cluster plugin add-local example-plugin org.deltafi.example

# Build and install the example-plugin
cluster plugin build install
```

If you make changes to your plugin, you may re-run `cluster plugin build install` to update the plugin with your changes.

If you want to compile and execute tests for your plugin, you can do so from the `example-plugin` directory:
```bash
./gradlew build test
```

## Trying Out the New Plugin

Flows are versioned and packaged as part of your plugin source code. In a Java project they are located in `src/main/resources/flows`, in a Python project they are located in `src/flows/`.
Flows can reference both actions local to your plugin and any other actions that are running on your DeltaFi instance.
The skeleton plugin that was just generated contains a minimal complete flow with a minimal transform action that simply
passes data through unchanged.  At this point, the plugin is complete and can be built and installed.

- If you navigate to the [DeltaFi user interface plugins page](http://local.deltafi.org/config/plugins), you can see that 
the plugin was installed.
- The plugin instantiates a flow that can be enabled on the [flows page](http://local.deltafi.org/config/flows).  Look for
  the flow named `example-plugin-transform` and enable it.
- Now you can navigate to the [upload page](http://local.deltafi.org/deltafile/upload/) and upload a sample file.  Make 
  sure to select the example flow when you upload data.

The default implementation of the transform simply passes data through, which is not very interesting.  Now is the time to remedy that.

## Adding Some Logic to Implement the JsonToYamlAction Transformation

By default, the generated `example-plugin/src/main/java/org/deltafi/example/actions/JsonToYamlAction.java` reads 
the content that was ingressed and rewrites the content without modification. In this section 
we will be adding logic to convert Json data to Yaml and store the new content.

The first step is to add a Json ObjectMapper and Yaml ObjectMapper to the class:

```java
private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
```

Next we will replace the TODO with our logic to convert the data.  This implementation will
lower-case all the top level keys and reformat the results as YAML.
We will also modify the `transformResult.saveContent` line to write the new content, 
instead rewriting the original content. This all needs to be wrapped in a try/catch to 
handle any IOExceptions thrown while mapping the data. When an exception occurs, 
we will return an `ErrorResult` that can be used to easily debug the exception from 
the [Errors page](http://local.deltafi.org/errors).

```java
try {
    Map<String, String> data = JSON_MAPPER.readValue(content, Map.class);
    Map<String, String> lowerCaseKeys = new HashMap<>();
    for (Map.Entry<String, String> entry: data.entrySet()) {
        lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
    }

    byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

    transformResult.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
} catch (IOException e) {
    return new ErrorResult(context, "Failed to convert or store data", e);
}
``` 

After the modifications your class should look like:
```java
package org.deltafi.example.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
public class JsonToYamlAction extends TransformAction<ActionParameters> {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    JsonToYamlAction() {
        super("Converts arbitrary json to yaml");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull TransformInput transformInput) {
        TransformResult transformResult = new TransformResult(context);
        ActionContent actionContent = transformInput.content(0);
        byte[] content = actionContent.loadBytes();

        try {
            Map<String, String> data = JSON_MAPPER.readValue(content, Map.class);
            Map<String, String> lowerCaseKeys = new HashMap<>();
            for (Map.Entry<String, String> entry: data.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

            transformResult.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
        } catch (IOException e) {
            return new ErrorResult(context, "Failed to convert or store data", e);
        }

        return transformResult;
    }

}
```

Our implementation introduced a dependency on `jackson-dataformat-yaml`.  Add the needed 
dependency to `example-plugin/build.gradle:

```gradle
plugins {
    id 'org.deltafi.version-reckoning' version "1.0"
    id 'org.deltafi.plugin-convention' version "${deltafiVersion}"
    id 'org.deltafi.test-summary' version "1.0"
}

group 'org.deltafi.example'

ext.pluginDescription = 'A plugin that takes in json, normalizes the keys and outputs yaml'

dependencies {
    // Dependency needed by YamlFormatAction
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2'
}
```

## Testing Your Plugin

Now that your plugin has some new logic, you can rebuild and deploy your new plugin version.

```
cluster plugin build install
```

Generate some test data for your plugin:

`example-plugin/src/test/resources`:
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

`example-plugin/src/test/resources/test3.json`:
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

Once the plugin installation is complete you can enable the flows on the [flow config page](http://local.deltafi.org/config/flows)
To run data through the flow you can go to the [upload page](http://local.deltafi.org/deltafile/upload/), choose your ingress flow and upload a file.
There will be link to the DeltaFile after the file is uploaded.

You can see the results of all uploaded DeltaFiles in the [search page](Http://local.deltafi.org/deltafile/search?ingressFlow=example-ingress).

When `test1.json` is uploaded, the file should complete and be egressed.  However `test2.json` and `test3.json` result in errors based on our initial implementation.

The following changes to the load action should fix the problem:

`example-plugin/src/main/java/org/deltafi/example/actions/JsonToYamlAction.java`:
```java
package org.deltafi.example.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
public class JsonToYamlAction extends TransformAction<ActionParameters> {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    JsonToYamlAction() {
        super("Converts arbitrary json to yaml");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull TransformInput transformInput) {
        TransformResult transformResult = new TransformResult(context);
        ActionContent actionContent = transformInput.content(0);
        byte[] content = actionContent.loadBytes();

        try {
            Map<String, Object> data = JSON_MAPPER.readValue(content, Map.class);
            Map<String, Object> lowerCaseKeys = new HashMap<>();
            for (Map.Entry<String, Object> entry: data.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            byte[] yaml = YAML_MAPPER.writeValueAsString(lowerCaseKeys).getBytes();

            transformResult.saveContent(yaml, actionContent.getName() + ".yml", "application/yaml");
        } catch (IOException e) {
            return new ErrorResult(context, "Failed to convert or store data", e);
        }

        return transformResult;
    }

}
```

After making this code change, rebuild and reinstall the plugin:

```bash
cluster plugin build install
```

Now you can go to the [errors page](http://local.deltafi.org/errors) in the DeltaFi UI and resume the errored flows.  They should continue without error and egress well-formed YAML versions of the normalized input.

## Adding Another Flow to Your Plugin
New flows can be created under the `flows` directory. Any code changes or flow changes will require the docker image to be rebuilt via the `cluster plugin build install` command.
