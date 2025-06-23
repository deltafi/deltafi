# Docker Compose

## Prerequisites

To run DeltaFi with Docker Compose orchestration, you will need Docker or Docker Desktop installed and these minimum system requirements:

| Minimum Requirements | Linux | MacOS |
| ----- | ----- | ----- |
| Operating System | Redhat, Ubuntu, Rocky and other *nixes | MacOS Sequoia (15) and after |
| CPU cores | 4 | 4 |
| RAM | 8 Gb | 8 Gb |
| Storage | 150 Gb | 150 Gb |
| Docker version | Docker 23.x or newer | Docker Desktop v4.x or newer |

## Installation

Create a directory where you want your DeltaFi system to be installed and download the DeltaFi bundle.  Then run the Deltafi application.

```bash
VERSION=2.22.1 # Or whatever version you want to install
DELTAFI_INSTALL_DIR=~/deltafi # Or where you want it
OS=darwin # Or linux
ARCH=arm64 # Or amd64
mkdir -p "${DELTAFI_INSTALL_DIR}" && docker run --rm -v "${DELTAFI_INSTALL_DIR}":/deltafi deltafi/deltafi:${VERSION}-${OS}-${ARCH} && "${DELTAFI_INSTALL_DIR}"/deltafi
```

Follow the prompts in the installation wizard and then DeltaFi will be started automatically in compose orchestration.

## Configuration Files

There are several key configuration files that allow additional configuration of the system:

### `config.yaml`

`config.yaml` is located at `~/.deltafi/config.yaml` and is created during the initial install.  Your file should look something like this:

```yaml
orchestrationMode: Compose
deploymentMode: Deployment
coreVersion: 2.22.1
installDirectory: /Users/myuser/deltafi
dataDirectory: /Users/myuser/deltafi/data
siteDirectory: /Users/myuser/deltafi/site
development:
    repoPath: /Users/myuser/deltafi/repos
    coreRepo: git@gitlab.com:deltafi/deltafi.git
```

Of particular note are the following configuration parameters:
- `installDirectory` - used to relocate the base deltafi install location.
- `dataDirectory` - the location used for content, database and metric storage.  If an alternate storage partition is desired for data, that path can be configured here.
- `siteDirectory` - the location for various DeltaFi parameter configuration files.  

### `site/values.yaml`
The `values.yaml` file located in the site directory is used for various DeltaFi system configuration settings and orchestration parameter overrides.  A default file is generated that will give you hints to orchestration and system configuration options.

### `site/compose.yaml`
The `compose.yaml` file located in the site directory is used for direct extension and override of the DeltaFi compose orchestration.  The various DeltaFi services can be configured here.  Additional non-standard DeltaFi services can also be added here, allowing additional support services to be managed alongside the core DeltaFi services.
