# Getting started with a simple DeltaFi plugin

## Prerequisites

In order to start up a demo DeltaFi or to set up a DeltaFi development environment, you will need a MacOS system or a supported Linux system (currently tested on CentOS 8 and Rocky 9).  Preferably 8GB RAM, 200GB free disk space, and 4 CPU cores, but your mileage will vary according to your system specs.  Note that the installation process requires the installation of 3rd party software on the target system (like KinD, OpenJDK 17, etc.) as well as starting up a containerized Kubernetes cluster.  This process is highly automated.

The target system should also have the following installed:
- Docker or Docker Desktop (make sure your user account can access docker without sudo)
- curl
- A window manager for Linux systems (KDE, XFCE, etc.)
- A web browser (Google Chrome is preferred)

For development, it is recommended that an IDE like IntelliJ or Visual Studio Code is installed for writing plugin code.  The IDE will be presumed and not covered in this tutorial.


## Installing the development environment

To execute a singlestep install of the latest released version of DeltaFi in a self contained KinD (Kubernetes in Docker) cluster:

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

## Creating a skeleton plugin
A new plugin can be initialized using the `deltafi plugin-init` command. This will prompt for the information necessary to create the plugin. Alternatively, you can initialize a new plugin by passing a configuration file to the command: `deltafi plugin-init -f plugin-config.json`.

Below are the steps to generate the [example-project](https://gitlab.com/deltafi/example-plugin). This must be run in the parent directory of the `deltafi` directory that was created by the installer (your location after running the singlestep install process).

Create `plugin-config.json`:
```json
{
  "artifactId": "example-plugin",
  "groupId": "org.deltafi.example",
  "description": "A plugin that takes in json, normalizes the keys and outputs yaml",
  "pluginLanguage": "JAVA",
  "actions": [
    {
      "className": "JsonLoadAction",
      "description": "Maps incoming json keys to all lowercase and loads the result in a domain under example-json",
      "actionType": "LOAD"
    },
    {
      "className": "YamlFormatAction",
      "description": "Acts on any DeltaFile with a domain of example-json, converts the json to yaml",
      "actionType": "FORMAT"
    }
  ]
}
```

Generate the skeleton plugin with the following command:
```bash
deltafi plugin-init -f plugin-config.json
```

## Building a simple flow in your plugin
Flows are versioned and packaged as part of your plugin source code. In a Java project they are located in `src/main/resources/flows`, in a Python project they are located in `src/flows/`.
Flows can reference both actions local to your plugin and any other actions that are running on your DeltaFi instance.
The next sections walk through building the flows for the example project.

### Update the Ingress Flow
For this example, we do not require any transform actions, we will process the incoming data as-is in the load action.

We will remove the sample flows and create a new set of flows for our example plugin:

```bash
# Clean out the sample flow
rm -f example-plugin/src/main/resources/flows/sample*.json
```

Create `example-plugin/src/main/resources/flows/example-ingress.json`

```json
{
  "name": "example-ingress",
  "type": "INGRESS",
  "description": "Ingress flows require a load action and can optional contain one or more transform actions",
  "loadAction": {
    "name": "ExampleLoadAction",
    "type": "org.deltafi.example.actions.JsonLoadAction"
  }
}
```

Create `example-plugin/src/main/resources/flows/example-egress.json`

```json
{
  "name": "example-egress",
  "type": "EGRESS",
  "description": "Egress flows require a format action and an egress action and optionally can provide one or more validate actions",
  "includeIngressFlows": [
    "example-ingress"
  ],
  "formatAction": {
    "name": "ExampleFormatAction",
    "type": "org.deltafi.example.actions.YamlFormatAction",
    "requiresDomains": [
      "example-json"
    ]
  },
  "egressAction": {
    "name": "ExampleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "metadataKey": "deltafiMetadata",
      "url": "\${egressUrl}"
    }
  }
}
```

Create `example-plugin/src/main/resources/flows/variables.json`
```json
[
  {
    "name": "egressUrl",
    "description": "The URL to post the DeltaFile to",
    "dataType": "STRING",
    "required": true,
    "defaultValue": "http://deltafi-egress-sink-service"
  }
]
```

## Adding some logic to load and format actions

Add a Constants class (`example-plugin/src/main/java/org/deltafi/example/actions/Constants.java`):

```java
package org.deltafi.example.actions;

public class Constants {

    public static final String DOMAIN = "example-json";

}
```

Implement the JsonLoadAction class (`example-plugin/src/main/java/org/deltafi/example/actions/JsonLoadAction.java`):

```java
package org.deltafi.example.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonLoadAction extends LoadAction<ActionParameters> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    public JsonLoadAction() {
        super("Maps incoming json keys to all lowercase and loads the result in a domain under example-json");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull LoadInput loadInput) {
        try (InputStream is = loadContentAsInputStream(loadInput.firstContent().getContentReference())) {
            Map<String, String> incomingData = OBJECT_MAPPER.readValue(is, Map.class);

            Map<String, String> lowerCaseKeys = new HashMap<>();

            for (Map.Entry<String, String> entry: incomingData.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            LoadResult loadResult = new LoadResult(context, List.of());
            loadResult.addDomain(Constants.DOMAIN, OBJECT_MAPPER.writeValueAsString(lowerCaseKeys), "application/json");
            return loadResult;
        } catch (ObjectStorageException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

Implement the YamlFormatAction class (`example-plugin/src/main/java/org/deltafi/example/actions/YamlFormatAction.java`):

```java
package org.deltafi.example.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class YamlFormatAction extends FormatAction<ActionParameters> {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public YamlFormatAction() {
        super("Acts on any DeltaFile with a domain of sample-json, converts the json to yaml");
    }

    @Override
    public FormatResultType format(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull FormatInput formatInput) {
        String data = formatInput.getDomains().get(Constants.DOMAIN).getValue();
        FormatResult formatResult = new FormatResult(context, getName(formatInput));

        try {
            Map<String, String> domainData = JSON_MAPPER.readValue(data, Map.class);
            formatResult.saveContent(YAML_MAPPER.writeValueAsString(domainData).getBytes(), "application/yaml");
        } catch (ObjectStorageException | JsonProcessingException e) {
            return new ErrorResult(context, "Failed to convert or store data", e);
        }
        return formatResult;
    }

    String getName(FormatInput formatInput) {
        return formatInput.getSourceFilename() + ".yaml";
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(Constants.DOMAIN);
    }
}
```

Add the needed dependency to `example-plugin/build.gradle:

```gradle
plugins {
    id 'org.deltafi.version-reckoning' version "1.0"
    id 'org.deltafi.plugin-convention' version "\${deltafiVersion}"
    id 'org.deltafi.test-summary' version "1.0"
}

group 'org.deltafi.example'

ext.pluginDescription = 'A plugin that takes in json, normalizes the keys and outputs yaml'

dependencies {
    // Dependency needed by YamlFormatAction
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2'
}
```

## Building and Installing your plugin

DeltaFi has a development CLI command called `cluster` which we will use for this example.

```bash
# Install the DeltaFi passthrough plugin
deltafi plugin-install org.deltafi.passthrough:deltafi-passthrough:$(deltafi version)

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

## Testing your plugin

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

When test1.json is uploaded, the file should complete and be egressed.  However test2.json and test3.json result in errors based on our initial implementation.

The following changes to the load action should fix the problem:

`example-plugin/src/main/java/org/deltafi/example/actions/JsonLoadAction.java`:
```java
package org.deltafi.example.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonLoadAction extends LoadAction<ActionParameters> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    public JsonLoadAction() {
        super("Maps incoming json keys to all lowercase and loads the result in a domain under example-json");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull LoadInput loadInput) {
        try (InputStream is = loadContentAsInputStream(loadInput.firstContent().getContentReference())) {
            Map<String, Object> incomingData = OBJECT_MAPPER.readValue(is, Map.class);

            Map<String, Object> lowerCaseKeys = new HashMap<>();

            for (Map.Entry<String, Object> entry: incomingData.entrySet()) {
                lowerCaseKeys.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            LoadResult loadResult = new LoadResult(context, List.of());
            loadResult.addDomain(Constants.DOMAIN, OBJECT_MAPPER.writeValueAsString(lowerCaseKeys), "application/json");
            return loadResult;
        } catch (ObjectStorageException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

After making this code change, rebuild and reinstall the plugin:

```bash
cluster plugin build install
```

Now you can go to the [errors page](http://local.deltafi.org/errors) in the DeltaFi UI and resume the errored flows.  They should continue without error and egress well formed YAML versions of the normalized input.

## Adding an additional flow to your plugin
New flows can be created under the `flows` directory. Any code changes or flow changes will require the docker image to be rebuilt via the `cluster plugin build install` command.
