# Getting Started with a Demo Cluster

## Prerequisites

Ensure that your system meets the [prerequisites](/kind#prerequisites) for installing and running a demo instance of DeltaFi in a self-contained KinD (Kubernetes in Docker) cluster.


## Installing the Demo Environment

To execute a single-step install of the latest released version of DeltaFi in a self-contained KinD cluster:

```bash
curl -fsSL https://gitlab.com/deltafi/installer/-/raw/main/kind-install.sh > kind-install.sh
chmod +x kind-install.sh
./kind-install.sh
```

The UI can be accessed at `http://local.deltafi.org` and the Grafana metrics dashboard can be accessed at `http://metrics.local.deltafi.org/dashboards`.  You should visit those links in your browser to verify that the installation process is complete.
