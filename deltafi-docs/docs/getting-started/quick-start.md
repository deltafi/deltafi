# Quick Start with Docker Compose

## Quick Install

Set some environment variables for your configuration:

```bash
VERSION=__VERSION__ # Or whatever version you want to install
DELTAFI_INSTALL_DIR=~/deltafi # Or where you want it
```

Note: Latest released versions and change logs can be found on the [DeltaFi release page](https://gitlab.com/deltafi/deltafi/-/releases).

Also, additional variables need to be set for your specific OS and architecture:

### Linux

```bash
OS=linux
ARCH=amd64 # Or arm64
```

### MacOS

```bash
OS=darwin
ARCH=arm64 # Or amd64
```

Install DeltaFi:

```bash
mkdir $DELTAFI_INSTALL_DIR
# This will install the deltafi executable from the official docker container
docker run --rm -v "${DELTAFI_INSTALL_DIR}":/deltafi deltafi/deltafi:${VERSION}-${OS}-${ARCH}
# Kick off the DeltaFi install
"${DELTAFI_INSTALL_DIR}"/deltafi
```

After the installation wizard has completed you will have a running and fully functional DeltaFi system.

In order to use the command line interface, you will either need to open a new shell or source your rc file like so:

```bash
. ~/.bashrc # or .zshrc if that is your thing
```

It is recommended that you install [Bash completion](https://github.com/scop/bash-completion) if you are a bash user to benefit from TUI command line tab completion support.

## Command Line Tour

DeltaFi has a feature rich Text User Interface (TUI) that is accessed with the `deltafi` command.

```bash
deltafi --help  # see what it can do!
```

### Ingress a file and see what happens

```bash
# Turn on a passthrough data source
deltafi data-source start --all-actions passthrough-rest-data-source
deltafi graph data-source
```

You should see a tree for the flows associated with the `passthrough-rest-data-source` like so:

```
◡ ▶ passthrough-rest-data-source
└─◎ passthrough
  └─◇ ▶ passthrough-transform
    └─◎ passthrough-egress
      └─▻ ▶ passthrough-data-sink
```

Ingress an ephemeral file (you can also ingress any file in your file system...)

```bash
deltafi ingress -d passthrough-rest-data-source <(echo "Well, hello there...")
```

The ingress result will have a file entry for your new DeltaFile with a DID (DeltaFile ID) that will be a UUID that looks something like `2f4a0fff-bbbd-4ed0-8a4e-6a86bbea13a9`.  You can examine your file and all the processing that occurred (using your UUID):

```bash
deltafi deltafile 2f4a0fff-bbbd-4ed0-8a4e-6a86bbea13a9
```

You can also use the search engine to interactively view DeltaFiles:

```bash
deltafi search
```

## Explore the GUI

The DeltaFi UI will be running on port 80 on your local system.  You can view the UI at [http://localhost/](http://localhost/).  You can also explore the rest of the system including the DeltaFile you ingressed:

- [http://localhost/deltafile/search](http://localhost/deltafile/search) - See all recent DeltaFiles.  Click through to your new DeltaFile.
- [http://localhost/deltafile/upload](http://localhost/deltafile/upload/) - Upload new DeltaFiles from the UI, analogous to the `deltafi ingress` command
- [http://localhost/metrics/system](http://localhost/metrics/system) - View system statistics
- [http://localhost/system-map](http://localhost/system-map) - Examine a map of all flows configured in the system

Now that you have a running DeltaFi system, you can continue to [extending DeltaFi by installing plugins](/getting-started/install-plugins) or even [creating your own plugins](/getting-started/simple-plugin)
