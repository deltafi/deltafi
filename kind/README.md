# Cluster Integration Testing with KinD

When developing the DeltaFi core and integrating DeltaFi plugins, it is possible to
deploy a local, disposable and quickly modifiable Kubernetes cluster using
[Kubernetes In Docker (KinD)](https://kind.sigs.k8s.io/).  A DeltaFi KinD cluster
provides a rapid, iterative test environment to test core changes, version
compatibility, and end-to-end flows in an environment with reasonable fidelity
to a full DeltaFi Kubernetes deployment.

The DeltaFi KinD cluster provides all functionality, including the DeltaFi UI, the CLI,
the Kubernetes Dashboard, the complete metrics and logging stack with Grafana, authentication,
and all core components.  The cluster has been extensively used on MacOS with x86 and Apple Silicon, and
should be reasonably easy to start on non-air gapped Linux environments as well.

## Setting up an integration cluster

### Prerequisites

For a MacOS environment, the following is needed:

- Hardware: At least 4 cores allocated to docker, 40-50Gb of clear disk space, and 8Gb RAM allocated to the Docker VM.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and provisioned with needed cores and RAM.
- [Homebrew](https://brew.sh/) installed
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Installation (MacOS specific)

#### Install dependencies

At a bare minimum, you will need to have the following tools installed:

- docker
- git
- helm
- kubectx (for kubectl)
- jq
- kind

For a quick install of all dependencies, in the `deltafi` repository:

```bash
kind/install.sh && cluster prerequisites
```
For local builds (`cluster loc build`) you must have JDK 11 installed.  `cluster prerequisites` will install
OpenJDK@11 via brew, but you may need to add it to your path in order to properly build.  The `cluster` script
will tell you if your Java JDK version is incorrect.

#### Check out your code

You should have a single subdirectory where you checkout `deltafi`, `deltafi-ui`, `deltafi-stix`, `deltafi-passthrough`, and any other
plugins that you will be testing.  The `cluster` script will automatically check out the UI, stix, and passthrough repos for you if
you have not done it ahead of time.  When the cluster is created, the cluster node will mount the directory above `deltafi` into
the node.  Only repositories in this tree will be accessable to the cluster.

#### Install the CLI tools

In the `deltafi` repository:

```bash
kind/install.sh
```

After this, the `deltafi` and `cluster` commands will be in your path and ready to execute.

## Getting Started

To start up your kubernetes cluster:

```bash
cluster up
```

To install the current released version (based on the repo version checked out) of DeltaFi into the cluster:

```bash
cluster install
# or...
deltafi install
```

When this command completes, you will have a fully functional core DeltaFi cluster running in KinD.

To install a locally built version of DeltaFi (from docker images built locally):

```bash
cluster loc build install
```

This command will build and install the local versions of DeltaFi core, stix, passthrough and UI.

After you install DeltaFi on the cluster, you will be able to point your browser at [local.deltafi.org](http://local.deltafi.org)
and interact with the DeltaFi UI.  You can install plugins, enable flows, upload data, check metrics, etc.

## `cluster` CLI

The following are some of the commands available in the `cluster` command line tool.  Use `cluster help` for more info.

- `cluster prerequisites` - Install prerequisites for running a KinD cluster.  __MacOS only.__
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

The `deltafi` CLI command is wrapped in a proxy so that all commands are running on the cluster node.
Since the CLI is referencing paths on the _node_ file system, paths in the local file system will not align.

The `cluster shell` is useful to allow persistent shell access to the node cluster.  When in this shell,
the `deltafi` CLI is fully functional relative to the node local file system.  This shell also persists via
[tmux](https://github.com/tmux/tmux/wiki) which allows for terminal multiplexing and session persistence as
long as the cluster is running.  To exit the shell without terminating the session, execute a tmux detach (`<C-b d>`)

Persistent volumes are all mounted in the `<deltafi repo>/kind/data` directory.

## Pro Tips

`helm` and `kubectl` will work with your KinD cluster just like a native cluster installation.

If you are using the Chrome browser (preferred) to interact with the UI, you may get certificate errors.  If the
browser prevents navigation to the page, type `thisisunsafe` in the browser to circumvent the certificate nanny.  
Crazy, but it works.

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
