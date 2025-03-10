# KinD Cluster for Demo, Dev, and Test

DeltaFi offers a local, disposable, and quickly modifiable Kubernetes cluster using
[Kubernetes In Docker (KinD)](https://kind.sigs.k8s.io/) with reasonable fidelity
to a full DeltaFi Kubernetes deployment.  A DeltaFi KinD cluster provides a rapid,
iterative environment to support the use cases of:
- Demonstration
- Plugin development
- Core development
- Integration testing, including:
   - Version compatibility
   - Core upgrades
   - End-to-end flows

The DeltaFi KinD cluster provides all functionality, including the DeltaFi UI, the CLI,
the Kubernetes Dashboard, the complete metrics and logging stack with Grafana, authentication,
and all core components.  The KinD cluster has been extensively tested in the environments noted in the [Prerequisites section](#prerequisites), and should be easy to start on non-air gapped Linux environments as well.

## Prerequisites

To start up a demo DeltaFi or to set up a DeltaFi development environment, your setup will need to meet the following requirements for hardware, OS, and packages with configurations.  Note that the installation process requires the installation of 3rd party software on the target system (like KinD, OpenJDK 21, etc.) as well as starting up a containerized Kubernetes cluster.  This process is highly automated.

### Hardware

Minimum system resources:
  - 4 CPU cores
  - 8 GB RAM
  - 50 GB free disk space minimum, preferably 100 GB to 200 GB depending on tool and data needs

For 1.x DeltaFi only:
  - support for and OS access to (including for the guest VM, if using virtualization) the AVX instruction set
     - verify AVX support with `cat /proc/cpuinfo | grep avx`


### Operating System

Supported operating systems include:
- MacOS 14 (x86 and ARM64)
- CentOS 8
- Rocky 9
- Ubuntu
   - 22.04.3
   - 24.04.1

### VM-based Setups

VM-based deployments of DeltaFi have been tested on the following configurations:

**For a Windows 11 host:**
- VMware Workstation 17 Player/Pro with guest VMs:
  - Ubuntu 22.04.3
- VirtualBox 6.1 with guest VMs:
  - CentOS 8
- VirtualBox 7.0 with guest VMs:
  - Ubuntu 24.04.1

**For a Linux host:**
- VirtualBox 6.1 with guest VMs:
  - Ubuntu 22.04.3


### Packages and Configurations

The following tools must be installed and configured as follows:
   - [Docker](https://docs.docker.com/engine/install/) or [Docker Desktop](https://docs.docker.com/desktop/), where your user can [access Docker without sudo](https://docs.docker.com/engine/install/linux-postinstall/)
   - curl
   - A window manager for Linux systems (KDE, XFCE, etc.)
   - A web browser ([Google Chrome](https://www.google.com/chrome/) is preferred)


## Installing the KinD Cluster

### Installing a Demo Environment

To execute a single-step install of the latest released version of DeltaFi in a self-contained KinD (Kubernetes in Docker) cluster:

```bash
curl -fsSL https://gitlab.com/deltafi/installer/-/raw/main/kind-install.sh > kind-install.sh
chmod +x kind-install.sh
./kind-install.sh
```
The UI can be accessed at `http://local.deltafi.org` and the Grafana metrics dashboard can be accessed at `http://metrics.local.deltafi.org/dashboards`.  You should visit those links in your browser to verify that the installation process is complete.


### Installing a Development or Integration Testing Environment

To execute a singlestep install of the latest released version of DeltaFi in a self-contained KinD (Kubernetes in Docker) cluster:

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


#### Check out your code

You should have a single subdirectory where you checkout `deltafi`, `deltafi-ui`, `deltafi-stix`, and any other
plugins that you will be testing.  When the cluster is created, the cluster node will mount the directory above `deltafi` into
the node.  Only repositories in this tree will be accessable to the cluster.

## Getting Started

To start up your kubernetes cluster:

```bash
cluster up
```

To install the current released version (based on the repo version checked out) of DeltaFi into the cluster:

```bash
cluster install
```

To install a locally built version of DeltaFi (from docker images built locally):

```bash
cluster loc build install
```

After you install DeltaFi on the cluster, you will be able to point your browser at [local.deltafi.org](http://local.deltafi.org)
and interact with the DeltaFi UI.  You can install plugins, enable flows, upload data, check metrics, etc.

## `cluster` CLI

The following are some of the commands available in the `cluster` command line tool.  Use `cluster help` for more info.

- `cluster up` - Start a KinD cluster on your local box.  You can poke at this cluster whichever way with `kubectl` and whatnot.  It is just a cluster running in a docker container.
- `cluster down` - Stop the kind cluster (and anything running in it).
- `cluster destroy` - Stop the kind cluster and nuke persistent volumes, for that clean, fresh feeling.
- `cluster install` - Start up a release DeltaFi (whatever is pointed to in the local working copy, like `deltafi install`)
- `cluster uninstall` - Stop a running DeltaFi (like `deltafi uninstall`) and leave cluster otherwise intact.
- `cluster run <blah>` - Run a command inside the kind control node. ex. `cluster run deltafi versions`
- `cluster shell` - Launch a shell tmux session inside the kind control node.  You can do all the Linux here.
- `cluster loc <subcommands>` - Local build operations.  This command is used to control the building and installing of local builds of DeltaFi.  This is optimized for quick turnaround development cycles.  The various subcommands:
  - `build` - Build a complete set of DeltaFi docker images locally.  You must have deltafi and the plugin repos checked out at the same directory level.
  - `clean` - Modifies build to be a clean build instead of an incremental one.
  - `install` - Install DeltaFi and all the plugins that are built locally.  Whatever is currently running and up to date will just keep on running.
  - `reinstall` - Uninstall and install DeltaFi and all the plugins.
  - `restart` - Down the whole cluster and then do an install
  - `bounce <something>` - Restart a particular pod or set of pods as a substring match.  `cluster loc bounce core` will bounce deltafi-core-.*.  `cluster loc bounce deltafi` will restart all the things.
  - `-f or force` - Throw caution to the wind and skip "Are you sure?"  Just ask yourself, "Are you sure?"
  - Any of the subcommands can be strung together to create a tasty workflow stew:
    - `cluster loc clean build restart -f` - Build all the things, start up a new cluster running it
    - `cluster loc build bounce deltafi-api` - For those quick turnaround builds of deltafi-api
    - `cluster loc make me a sammich` - You will get helpful usage tips

## Under the Hood

The cluster command manages a single node KinD cluster, which is a fully functional Kubernetes cluster
running within a Docker container.  This allows for the entire cluster to be disposed and rebuilt very
quickly.  In addition to the cluster, three Docker registry containers are launched to support Docker
image sideloading and caching.  These registries persist even when the cluster is destroyed, allowing
faster startup times and air-gapped operation once the images have been downloaded the first time.

Since the kubernetes cluster is not running on bare metal, there are some capabilities of the `deltafi` CLI
that do not function as expected from the command line.  For example, `deltafi uninstall` will work
as expected, operating directly on the cluster, but `deltafi plugin-install <foo>` will have surprising results.
Since the CLI is referencing paths on the _node_ file system, paths in the local file system will not align.

The `cluster shell` is useful to allow persistent shell access to the node cluster.  When in this shell,
the `deltafi` CLI is fully functional relative to the node local file system.  This shell also persists via
[tmux](https://github.com/tmux/tmux/wiki) which allows for terminal multiplexing and session persistence as
long as the cluster is running.  To exit the shell without terminating the session, execute a tmux detach (`<C-b d>`)

Persistent volumes are all mounted in the `<deltafi repo>/kind/data` directory.

## Pro Tips

`helm` and `kubectl` will work with your KinD cluster just like a native cluster installation.

It is helpful to run a [tmux](https://github.com/tmux/tmux/wiki) session when you are interacting with a cluster.  The
default kubernetes namespace is set to `deltafi`, and it is helpful to always run a k8s pod watcher in a terminal pane.
The following script can be used as a handy shortcut:

```bash
#!/bin/bash

# kw - kubectl watcher (e.x.: kw get pods)

if command -v kubecolor > /dev/null; then
  watch -ctn 0.5 kubecolor "${@}" --force-colors
elif command -v kubectl > /dev/null; then
  watch -ctn 0.5 kubectl "${@}"
fi
```

You can also use `k9s` for monitoring and interacting with the cluster, and `lazydocker` for
interacting with docker.  These are included in the dependency `Brewfile`, along with
`kail`, `kubecolor`, and `tree`.  You're welcome.



## Additional Reference for MacOS Systems

### Setting up an integration cluster

#### Prerequisites

For a MacOS environment, the following is needed:

- Hardware: At least 4 cores allocated to docker, 40-50Gb of clear disk space, and 8Gb RAM allocated to the Docker VM.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and provisioned with needed cores and RAM.
- [Homebrew](https://brew.sh/) installed
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### Installation (MacOS specific)

##### Install dependencies

At a bare minimum, you will need to have the following tools installed:

- docker
- git
- helm
- kubectx (for kubectl)
- jq
- kind

For a quick install of all dependencies, in the `deltafi` repository:

```bash
brew bundle --file kind/Brewfile
```

##### Install the CLI tools

In the `deltafi` repository:

```bash
deltafi-cli/install.sh
kind/install.sh
```

After this, the `deltafi` and `cluster` commands will be in your path and ready to execute.


