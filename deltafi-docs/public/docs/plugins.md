# Creating a Plugin

To create Actions and Flows for your organization, you need to start by creating a plugin.

Plugins are delivered in a Docker image. When installed, a Kubernetes pod is launched where the Actions run inside.
Plugins created with the DeltaFi Action Kit will automatically register with the DeltaFi Core at startup.
Registration identifies all Actions, Action Parameter classes, and general information about the plugin.
Typically, a plugin includes one or more Flows which use the custom Actions and Flow Variables, but they are both
optional. Flows and Flow Variables are also included in the Plugin registration.

A plugin project can be created in a variety of ways. The simplest way to start is using a single Git repository to host
a single Plugin, which builds with Gradle. DeltaFi provides two examples for this: one in Java, and one in Python. For
Java, DeltaFi provides a custom Gradle plugin to facilitate the Docker build and Plugin structure. The Python structure
requires a few extra files.

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
hierynomusLicenseVersion=0.16.1

deltafiMavenRepo=https://gitlab.com/api/v4/projects/25005502/packages/maven
projectMavenRepo=https://gitlab.com/api/v4/projects/34705336/packages/maven

localDockerRegistry=localhost:5000
```

Finally, you need some special setup in your `build.gradle` to create the plugin. By using the latest
`org.deltafi.plugin-convention` your Gradle tasks can generate a Spring Boot Docker image which automatically has the
correct hooks to register your Plugin with DeltaFi. A local Dockerfile is not necessary.

```groovy
plugins {
    id 'org.deltafi.git-version' version '0.1.0'
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
  id 'org.deltafi.git-version' version '0.1.0'
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

from actions.load_action import MyLoadAction
from actions.transform_action import MyTransformAction
from deltafi.plugin import Plugin

Plugin([MyLoadAction, MyTransformAction],
    "This is the description of the demo plugin").run()
```

## Flows

Flows may be defined for a Plugin. See [Flows](/flows).


## Testing Plugin Actions

Java based actions can be tested with standard unit tests along with the helpers in the `deltafi-action-kit-test` dependency. See [Java Unit Testing](/unit-test) 
