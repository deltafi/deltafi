# Creating a Plugin

To create Actions and Flows for your organization, you need to start by creating a plugin.

Plugins are delivered in a Docker image. When installed, a Kubernetes pod is launched where the Actions run inside.
Plugins created with the DeltaFi Action Kit will automatically register with the DeltaFi Core at startup.
Registration identifies all Actions, Action Parameter classes, and general information about the plugin.
Typically, a plugin includes one or more Flows which use the custom Actions and Flow Variables, but they are both
optional. Flows and Flow Variables are also included in the Plugin registration.

A plugin project can be created in a variety of ways. The simplest way to start is using a single Git repository to host
a single Plugin, which builds with Gradle. DeltaFi provides examples for this in Java, Python, Go, and C++. For
Java, DeltaFi provides a custom Gradle plugin to facilitate the Docker build and Plugin structure. The Python structure
requires a few extra files. The Go structure uses standard Go modules and a multi-stage Docker build.

## Java

The overall Java structure is shown below and requires downloading the deltafi-action-kit JAR from a Gitlab/Maven
repository.

```
myplugin/
| - src/main/java/...
| - src/main/resources/flows/
| - build.gradle
| - gradle.properties
| - settings.gradle
```

The `src` directory is where your Actions will be developed using normal Java conventions
(main/java/..., main/resources, test/java/..., test/resources, etc).


### Gradle Files

Start with a `settings.gradle`, where we'll set up access to Maven repositories.

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url deltafiMavenRepo
            name "GitLab"
            credentials(HttpHeaderCredentials) {
                name = gitLabTokenType
                value = gitLabToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url deltafiMavenRepo
            name "GitLab"
            credentials(HttpHeaderCredentials) {
                name = gitLabTokenType
                value = gitLabToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}
```

Next we'll use the `gradle.properties` to store a few variables. Set deltafiVersion to your target action-kit version.

```groovy
org.gradle.jvmargs=-Xmx1024M
org.gradle.logging.level=INFO
systemProp.org.gradle.internal.http.socketTimeout=120000
systemProp.org.gradle.internal.http.connectionTimeout=120000

deltafiVersion=0.100.0

deltafiMavenRepo=https://gitlab.com/api/v4/projects/25005502/packages/maven
projectMavenRepo=https://gitlab.com/api/v4/projects/34705336/packages/maven

localDockerRegistry=localhost:5000
```

Finally, you need some special setup in your `build.gradle` to create the plugin. By using the latest
`org.deltafi.plugin-convention` your Gradle tasks can generate a Spring Boot Docker image which automatically has the
correct hooks to register your Plugin with DeltaFi. A local Dockerfile is not necessary.

```groovy
plugins {
    id "org.deltafi.plugin-convention" version "${deltafiVersion}"
}

group 'org.myorg.plugingroup'

ext.pluginDescription = 'My plugin actions'
```

## Python

The Python action kit for DeltaFi is published on [PyPi](https://pypi.org/project/deltafi/). A project structure for a
DeltaFi Python Plugin is shown below:

```
build.gradle
DockerFile
gradle.properties
settings.gradle
src/flows/
src/plugin.py
src/actions/...
src/pyproject.toml:
```

A DeltaFi Plugin can easily be written and built using a Gradle and Poetry framework. A set of skeleton files are
provided below.

### Skeleton Files

Start with a `build.gradle` that includes the sections below. It needs the docker plugin and associated assembly
properties, the group variable, and poetry commands.

```groovy
plugins {
  id 'org.deltafi.git-version' version "2.0.1"
  id "com.palantir.docker" version "${palantirDockerVersion}"
}

group 'org.deltafi.python-poc'

task clean(type: Delete) {
  delete 'src/dist'
}

task test {}

task replaceDeltafiVersion(type: Exec) {
  def deltafiVersion = project.deltafiVersion
  def tokens = deltafiVersion.tokenize('.')
  def patchfull = tokens.get(2)
  def patch = patchfull.tokenize('-').get(0)

  if (deltafiVersion.contains("SNAPSHOT")) {
    deltafiVersion = ">=${tokens.get(0)}.${tokens.get(1)}.${patch}rc0"
  } else {
    deltafiVersion = "==${tokens.get(0)}.${tokens.get(1)}.${tokens.get(2)}"
  }
  commandLine 'sed', "s/DELTAFI_VERSION/${deltafiVersion}/g", 'src/pyproject.toml.template'
  standardOutput new FileOutputStream('src/pyproject.toml')
}

task setupPoetry(type: Exec) {
  commandLine 'pip3', '-q', 'install', 'poetry'
}

task assemble(type: Exec) {
  dependsOn replaceDeltafiVersion, setupPoetry

  workingDir 'src'
  commandLine 'poetry', 'lock'
}

docker {
  dependsOn assemble

  name "${project.name}:${project.version}"
  tag 'local', "${localDockerRegistry}/${project.name}:latest"
  def args = ['PROJECT_GROUP': group, 'PROJECT_NAME': project.name, 'PROJECT_VERSION': project.version]
  if (project.hasProperty('gitLabToken')) {
    args['GITLAB_TOKEN'] = gitLabToken
    args['GITLAB_USER'] = "__token__"
  }
  buildArgs(args)
  copySpec.from('src').include('**').into('src')
}
```

A `Dockerfile` must be created to create the Python Plugin image. To satisfy the DeltaFi Python dependencies, the base
image must use a minimum Python version of 3.7. The `PROJECT` variables are needed for proper registration with DeltaFi.
The entrypoint for the image is typically `plugin.py`, but may be changed.

```
FROM python:3.7-slim

ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

WORKDIR /code

COPY src/. /code/
RUN pip install poetry && poetry config virtualenvs.create false

RUN poetry install

ARG PROJECT_NAME
ARG PROJECT_GROUP
ARG PROJECT_VERSION

ENV PROJECT_NAME $PROJECT_NAME
ENV PROJECT_GROUP $PROJECT_GROUP
ENV PROJECT_VERSION $PROJECT_VERSION

ENTRYPOINT [ "python", "/code/plugin.py" ]
```

Create the `gradle.properties` with the settings below.

```groovy
org.gradle.jvmargs=-Xmx1024M
org.gradle.logging.level=INFO
systemProp.org.gradle.internal.http.socketTimeout=120000
systemProp.org.gradle.internal.http.connectionTimeout=120000

palantirDockerVersion=0.22.1
deltafiVersion=0.100.0
localDockerRegistry=localhost:5000
```

### Poetry

Poetry is used to install the plugin when building the Docker image. Building and installing your Plugin with Poetry
requires a `src/pyproject.toml` file. Use the example below. Make sure the version of `deltafi` dependency is compatible
with your running DeltaFi Core.

```
[tool.poetry]
name = "myplugin-package-name"
version = "0.1.0"
description = "This is my package description"
authors = ["Somebody <somebody@domain.com>"]
readme = "README.md"
packages = []

[tool.poetry.dependencies]
python = "^3.7"
deltafi = "0.100.0"

[build-system]
requires = ["poetry-core"]
build-backend = "poetry.core.masonry.api"
```

### Python Entrypoint

An entrypoint Python script is needed to start the Plugin when the Docker container is deployed. The entrypoint script
must identify the Actions to the Plugin class and call the `run()` method. See the following `src/plugin.py` example.
Update the Action name and module to match your first Action.

```python
#!/usr/bin/env python3

from actions.transform_action import MyTransformAction
from deltafi.plugin import Plugin

Plugin([MyTransformAction],
    "This is the description of the demo plugin").run()
```

## Go

::: warning Beta
The Go action kit is currently in beta. Interfaces may change in future releases.
:::

The Go action kit is published as a Go module. A project structure for a DeltaFi Go Plugin is shown below:

```
myplugin/
├── go.mod
├── go.sum
├── main.go
├── plugin.yaml
├── Makefile
├── Dockerfile
├── actions/
│   ├── my_transform_action.go
│   ├── my_transform_action_params.go
│   ├── my_transform_action_params.json   (optional, see "Schema round-trip")
│   ├── my_transform_action.yaml
│   └── my_transform_action_test.go
├── flows/
│   └── my-transform.json
└── integration/
    └── basic-test.json
```

Each action ships in two Go files:

* `<name>_action.go` — the action struct, `init()` registration, and the action method
* `<name>_action_params.go` — the typed `*Params` struct only

Splitting the params struct into a sibling file keeps the action focused on logic and gives the schema round-trip tooling (described below) a deterministic location to read from and write to.

### Go Module

Start with a `go.mod` that imports the action kit:

```
module gitlab.com/deltafi/deltafi/my-plugin

go 1.26

require gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2 v2.52.3
```

### Go Entrypoint

The entrypoint registers actions and starts the plugin. Actions self-register via `init()` functions, so they only need a blank import. The `RunPlugin` function handles all plugin lifecycle boilerplate.

```go
package main

import (
    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
    _ "gitlab.com/deltafi/deltafi/my-plugin/actions"
)

func main() {
    actionkit.RunPlugin()
}
```

Plugin identity lives in `plugin.yaml` at the repo root:

```yaml
groupId: org.myorg
artifactId: my-plugin
domain: org.myorg.my-plugin
description: My DeltaFi plugin
```

The Makefile extracts these fields from `plugin.yaml` and passes them to the Docker build, which stamps them into the binary via `-ldflags -X` so production binaries are self-contained. When running locally via `go run .`, the kit falls back to reading `./plugin.yaml` directly. Version is supplied at link time via `-X gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2.PluginVersion=<version>` and defaults to `0.0.0-dev`.

### Parameters and schema round-trip

The kit reads the following struct tags on parameter fields when it generates the JSON Schema that core uses to render the DeltaFi UI:

| Tag | Effect |
|---|---|
| `json:"name"` | parameter name in the UI |
| `description:"..."` | human-readable description |
| `default:"..."` | default value (string form; coerced to the field type) |
| `enum:"a,b,c"` | allowed values (renders as a dropdown) |
| `required:"true"` | marks the parameter as required |
| `minimum:"0"` / `maximum:"100"` | numeric bounds |

Supported field types: Go primitives (`bool`, `int`, `string`, `float64`), slices, string-keyed maps, pointers to named structs declared in the same params file, and the kit's `actionkit.DataSize` and `actionkit.EnvVar` helpers.

Two Make targets convert between the Go source and a standalone JSON Schema file:

```sh
make jsonFromParams   # writes actions/<name>_action_params.json from each _params.go
make paramsFromJson   # writes actions/<name>_action_params.go   from each _params.json
```

Both targets invoke `gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2/cmd/actionkit-schemagen`, a pure file-by-file AST tool — no plugin compilation required. `jsonFromParams` produces schemas that are byte-for-byte identical to what the kit sends to core during plugin registration (the AST tool reuses the kit's `actionkit.GenerateSchema` logic). `paramsFromJson` regenerates the Go source from a schema; the output is canonical (fields alphabetize, comments drop, slice-of-struct fields come back as slice-of-pointer-to-struct) but functionally equivalent.

Either direction is the source of truth; pick the workflow that fits the team. Round-tripping is idempotent from the first JSON onward.

### Dockerfile

Go plugins use a multi-stage Docker build:

```dockerfile
FROM golang:1.26-alpine AS builder

WORKDIR /build
COPY deltafi-go-action-kit/go.mod deltafi-go-action-kit/go.sum ./deltafi-go-action-kit/
COPY my-plugin/go.mod my-plugin/go.sum ./my-plugin/

WORKDIR /build/my-plugin
RUN go mod download -x

WORKDIR /build
COPY deltafi-go-action-kit/ ./deltafi-go-action-kit/
COPY my-plugin/ ./my-plugin/

WORKDIR /build/my-plugin
ARG VERSION=0.0.0-dev
ARG DESCRIPTION=My DeltaFi plugin
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags "\
  -X main.version=${VERSION} \
  -X 'main.description=${DESCRIPTION}'" \
  -o my-plugin .

FROM alpine:3.23
RUN apk --no-cache add ca-certificates
WORKDIR /app
COPY --from=builder /build/my-plugin/my-plugin deltafi-plugin
COPY --from=builder /build/my-plugin/flows ./flows
RUN chmod +x deltafi-plugin
ENTRYPOINT ["/app/deltafi-plugin"]
```

### Makefile

A Makefile simplifies common tasks:

```makefile
VERSION ?= $(shell git describe --tags --always --dirty)

.PHONY: build test vet

build:
	go build -ldflags "-X main.version=$(VERSION)" -o my-plugin .

test:
	go test ./...

vet:
	go vet ./...
```

## C++

::: warning Beta
The C++ action kit is currently in beta. Interfaces may change in future releases.
:::

The C++ action kit is a header-only library. A project structure for a DeltaFi C++ Plugin is shown below:

```
myplugin/
├── CMakeLists.txt
├── Makefile
├── Dockerfile
├── main.cpp
├── actions/
│   └── transform.hpp
├── flows/
│   └── my-transform.json
└── integration/
    └── basic-test.json
```

### CMake

Start with a `CMakeLists.txt` that imports the action kit:

```cmake
cmake_minimum_required(VERSION 3.20)
project(my_plugin VERSION 0.1.0 LANGUAGES CXX)

set(DELTAFI_ACTION_KIT_PATH "" CACHE PATH "Local path to deltafi-cpp-action-kit")

if(DELTAFI_ACTION_KIT_PATH)
    add_subdirectory(${DELTAFI_ACTION_KIT_PATH}
                     ${CMAKE_CURRENT_BINARY_DIR}/deltafi-cpp-action-kit)
else()
    include(FetchContent)
    FetchContent_Declare(deltafi_cpp_action_kit
        GIT_REPOSITORY https://gitlab.com/deltafi/deltafi.git
        GIT_TAG        main
        SOURCE_SUBDIR  deltafi-cpp-action-kit
    )
    FetchContent_MakeAvailable(deltafi_cpp_action_kit)
endif()

if(NOT DEFINED VERSION)
    set(VERSION "0.0.0-dev")
endif()

add_executable(my-plugin main.cpp)
target_link_libraries(my-plugin PRIVATE deltafi::action_kit)
target_compile_definitions(my-plugin PRIVATE VERSION="${VERSION}")
target_include_directories(my-plugin PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
```

### C++ Entrypoint

Actions self-register via `DELTAFI_ACTION` macros, so the entrypoint only needs to include action headers and use the `DELTAFI_PLUGIN` macro:

```cpp
#include <deltafi/plugin>
#include "actions/transform.hpp"

DELTAFI_PLUGIN(
    "org.myorg",
    "my-plugin",
    "My DeltaFi C++ plugin",
    "org.myorg.my-plugin"
)
```

### Dockerfile

C++ plugins use a multi-stage Docker build with static linking:

```dockerfile
FROM alpine:3.21 AS builder
RUN apk add --no-cache cmake ninja g++ git

WORKDIR /build
COPY deltafi-cpp-action-kit/ ./deltafi-cpp-action-kit/
COPY my-plugin/ ./my-plugin/

WORKDIR /build/my-plugin
ARG VERSION=0.0.0-dev
RUN cmake -B build -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DDELTAFI_ACTION_KIT_PATH=../deltafi-cpp-action-kit \
    -DVERSION=${VERSION} \
    -DCMAKE_EXE_LINKER_FLAGS="-static"
RUN cmake --build build

FROM gcr.io/distroless/static:nonroot
COPY --from=builder /build/my-plugin/build/my-plugin /app/deltafi-plugin
COPY --from=builder /build/my-plugin/flows /app/flows
ENTRYPOINT ["/app/deltafi-plugin"]
```

### Makefile

A Makefile simplifies common tasks:

```makefile
VERSION ?= $(shell git describe --tags --always --dirty)

.PHONY: build test clean

build:
	cmake -B build -DVERSION=$(VERSION) -DDELTAFI_ACTION_KIT_PATH=../deltafi-cpp-action-kit
	cmake --build build

test: build
	ctest --test-dir build --output-on-failure

clean:
	rm -rf build
```

## Flows

Flows may be defined for a Plugin. See [Flows](/flows).


## Lookup Tables

Lookup tables may be used by a Plugin. See [Lookup Tables](/lookup-tables).


## Testing Plugin Actions

Java actions can be tested with standard unit tests along with the helpers in the `deltafi-action-kit-test` dependency. Go actions use the test helpers built into the `deltafi-go-action-kit` package. C++ actions use the test helpers in the `deltafi/testing.hpp` header. See [Action Unit Testing](/unit-test).

To test complete data flows end-to-end, use integration tests. Integration tests validate that your data sources, transforms, and data sinks work together correctly. See [Integration Testing](/integration-test). 
