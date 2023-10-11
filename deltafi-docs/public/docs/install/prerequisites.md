# Prerequisites

Before installing DeltaFi, the prerequisites defined in this section should be satisfied. The reader of this guide should also be familiar with basic Linux command-line operations, including creating files, basic text editing, and installing packages.

## Hardware

DeltaFi can be installed on a single node but is designed to run in a multi-node cluster. Minimum hardware requirements will be highly dependent on the environment, number of nodes, and expected data volume. For a single-node dev/test cluster, we recommend the following:

* 4 CPU cores
* 16 GB RAM
* 100 GB
  * with at least 20 GB allocated to `/var`

Hardware support for the AVX instruction set is required.  Verify AVX support in your OS with ```cat /proc/cpuinfo | grep avx```.

## Operating system

DeltaFi was designed, built, and tested on CentOS. We recommend using a RedHat-based system for deployments (CentOS, Redhat, Rocky, etc.)

## SSH Key Authentication

SSH key authentication should be set up between all nodes. A user-chosen for installation (e.g. centos) should be created on all nodes and be able to ssh to all nodes without a password. The node where the installation steps are to be executed should have an ssh-agent running with the private key loaded. This can be verified with the following command:

```bash
ssh-add -l
```

You should see output similar to this:

```bash
2048 SHA256:cjE4161cfX+u5knxbx6Rib3kJ303JsQWlwjnY7wsT2g Username (RSA)
```

It should be noted that this requirement exists even in a single-node environment. You should be able to run the following command and not be prompted for a password:

```bash
ssh $(hostname) date
```

The output should show the current time.

## Packages

There are some packages that are required by the steps in this guide. They only need to be installed on the node where the installation steps are being executed. Install them using the following `yum` commands:

```bash
sudo yum install -y epel-release
sudo yum install -y git python3 ansible-python3
sudo ln -s /usr/bin/ansible-playbook-3 /usr/bin/ansible-playbook
sudo ln -s /usr/bin/ansible-galaxy-3 /usr/bin/ansible-galaxy
```
