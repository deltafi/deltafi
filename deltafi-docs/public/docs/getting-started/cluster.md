# Getting Started with a demo DeltaFi Cluster

## Prerequisites

To start up a demo DeltaFi or to set up a DeltaFi development environment, you will need:
- a supported OS
   - MacOS
   - a supported Linux OS (currently tested on CentOS 8, Rocky 9, and Ubuntu 22.04.3)
- minimum system resources
   - 4 CPU cores
   - 8 GB RAM
   - 50 GB free disk space minimum, preferrably 100 GB to 200 GB depending on tool and data needs
- hardware support for and OS access to the AVX instruction set
   - verify AVX support with ```cat /proc/cpuinfo | grep avx```
- tools installed
   - [Docker](https://docs.docker.com/engine/install/) or [Docker Desktop](https://docs.docker.com/desktop/), where your user can [access Docker without sudo](https://docs.docker.com/engine/install/linux-postinstall/)
   - curl
   - A window manager for Linux systems (KDE, XFCE, etc.)
   - A web browser ([Google Chrome](https://www.google.com/chrome/) is preferred)

Note that the installation process requires the installation of 3rd party software on the target system (like KinD, OpenJDK 17, etc.) as well as starting up a containerized Kubernetes cluster.  This process is highly automated.

## Recommended Initial Stacks

For running a demo DeltaFi or to set up a DeltaFi development environment in a VM, consider the currently tested configurations of:
- for Windows hosts
   - VMware with an Ubuntu 22.04.3 guest VM
- for Linux hosts
   - VMware or VirtualBox with an Ubuntu 22.04.3 guest VM

## Installation Instructions

To execute a single-step install of the latest released version of DeltaFi in a self-contained KinD (Kubernetes in Docker) cluster:

```bash
curl -fsSL https://gitlab.com/deltafi/installer/-/raw/main/kind-install.sh > kind-install.sh
chmod +x kind-install.sh
./kind-install.sh
```

The UI can be accessed at `http://local.deltafi.org`.
