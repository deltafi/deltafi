# Kubernetes

## Prerequisites

Before installing DeltaFi, the prerequisites defined in this section should be satisfied. The reader of this guide should also be familiar with basic Linux command-line operations, including creating files, basic text editing, and installing packages.

### Hardware

DeltaFi can be installed on a single node but is designed to run in a multi-node cluster. Minimum hardware requirements will be highly dependent on the environment, number of nodes, and expected data volume. For a single-node dev/test cluster, we recommend the following:

* 4 CPU cores
* 16 GB RAM
* 100 GB
  * with at least 20 GB allocated to `/var`

Hardware support for the AVX instruction set is required.  Verify AVX support in your OS with ```cat /proc/cpuinfo | grep avx```.

### Operating system

DeltaFi was designed, built, and tested on CentOS. We recommend using a RedHat-based system for deployments (CentOS, Redhat, Rocky, etc.)

### SSH Key Authentication

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

### Packages

There are some packages that are required by the steps in this guide. They only need to be installed on the node where the installation steps are being executed. Install them using the following `yum` commands:

#### RHEL8, RHEL9 or Rocky 9 Environment
```bash
sudo yum install -y epel-release
sudo yum install -y git python3 ansible-python3
sudo ln -s /usr/bin/ansible-playbook-3 /usr/bin/ansible-playbook
sudo ln -s /usr/bin/ansible-galaxy-3 /usr/bin/ansible-galaxy
```

#### RHEL 8 Environment 
```bash
sudo subscription-manager repos --enable codeready-builder-for-rhel-8-$(arch)-rpms
sudo dnf install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm
sudo yum install -y git python3 ansible
```

## Ansible Setup

The installation process for DeltaFi is handled by Ansible playbooks. Clone or extract the DeltaFi Ansible git repository into your home directory and install the dependencies by using the following commands:

```bash
cd ~
git clone git@gitlab.com:deltafi/ansible.git
cd ~/ansible
ansible-galaxy install -r requirements.yml
ansible-galaxy collection install -r requirements.yml
```

### Inventory File

The DeltaFi Ansible Playbooks act on an inventory file. This file describes your installation environment and instructs Ansible where to install what. The DeltaFi Ansible repository that you cloned/extracted in the previous step includes a few example inventory files in the `inventory` directory in the root of the repository. Copy one to a new file called `site` and modify it to include your host information and what roles you want each host to have.

- For a single-node example, see `inventory/test`
- For a multi-node example, see `inventory/dev`

For the purposes of this guide, we’ll assume an inventory file named `site`.

### Node roles

When looking at the example inventory files, notice the `deltafi_node_roles` field specified for each server. DeltaFi must have one or more `storage` nodes and one or more `compute` nodes. A node can have both roles, as is the case with the single-node example.

## Install Kubernetes

DeltaFi is designed to run in a Kubernetes cluster. In this section, we will set up a new RKE2 Kubernetes cluster using an Ansible playbook included with DeltaFi.

::: tip NOTE:
If you already have a Kubernetes cluster provisioned and would like to use that for installing DeltaFi, please skip to the [Install DeltaFi Core](/install/core) section of this guide.
:::

### Air-Gap Install

If you are installing in an air-gapped environment (not connected to the internet), please create a directory called `/data/rke2`:

```bash
sudo mkdir -p /data/rke2
sudo chown $USER:$USER /data/rke2
```

Place the following RKE2 v1.31.3+rke2r1 files in `/data/rke2`:

- `rke2-images.linux-amd64.tar.zst`
- `rke2.linux-amd64.tar.gz`
- `sha256sum-amd64.txt`

These files can be obtained [here](https://github.com/rancher/rke2/releases/tag/v1.31.3%2Brke2r1).

Also, place the Helm v3.16.4 binary in `/data/rke2`. It can be obtained [here](https://github.com/helm/helm/releases/tag/v3.16.4). Be sure to extract the binary and place it in `/data/rke2`.

Next, run the `install-rke2.yaml` playbook passing in the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section and setting `rke2_airgap_mode` to `true`.

```bash
cd ~/ansible
ansible-playbook install-rke2.yml -i inventory/site -e rke2_airgap_mode=true
```

### Online Install

If you are connected to the internet, simply run the `install-rke2.yml` playbook passing it the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section.

```bash
cd ~/ansible
ansible-playbook install-rke2.yml -i inventory/site -e rke2_airgap_mode=false
```

### Verification

The output from Ansible is very verbose. The important part is the PLAY RECAP at the very end. The output should look similar to this:

```
PLAY RECAP *************************************************************************
df-test-01   : ok=24 changed=6 unreachable=0 failed=0 skipped=13 rescued=0 ignored=0
```

Ensure that there are no failed tasks (`failed=0`).

Use the following command to verify that Kubernetes is installed and ready for DeltaFi installation:

```bash
kubectl get nodes
```

This should return a list of nodes that matches that of the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section. The output should look similar to this:

```bash
NAME         STATUS   ROLES                       AGE   VERSION
df-test-01   Ready    control-plane,etcd,master   1h    v1.31.3+rke2r1
```

## Install DeltaFi Core

In this section, we will install the DeltaFi Core.

### Bootstrap Kubernetes

Before installing the DeltaFi Core, we need to bootstrap the Kubernetes cluster. This process includes creating Kubernetes storage volumes and secrets.

Run the `bootstrap-deltafi.yml` playbook passing in the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section.

```
cd ~/ansible
ansible-playbook bootstrap-deltafi.yml -i inventory/site
```

### Install Core

Clone or extract the DeltaFi Core git repository into your home directory.

```
cd ~
git clone git@gitlab.com:deltafi/deltafi.git
```

Next, we’ll install the DeltaFi command-line interface ([TUI](/operating/TUI)). This provides the `deltafi` command - the primary way of interacting with the DeltaFi system. Use the following command to install the DeltaFi TUI.

```
~/deltafi/deltafi-cli/install.sh
```

This installs the deltafi command into `/usr/local/bin/deltafi`. Verify this with the `which` command:

```
which deltafi
```

Next, we will use the `deltafi install` command to install the core services of DeltaFi.

This process utilizes a [Helm](https://helm.sh/) values.yaml file for providing system configuration located in `~/deltafi/charts/deltafi/values.yaml`. Any of the values in this file can be overridden in a site-specific values file. For this install, let's create the file `~/site.values.yaml` with the following content:

```yaml
deltafi:
  ui:
    title: Example DeltaFi
ingress:
  domain: deltafi.example.com
  tls:
    enabled: true
    ssl_ciphers: "ECDHE-RSA-AES256-GCM-SHA384"
    secrets:
      default: deltafi-example-com
```

Create a [kubernetes tls secret](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_create/kubectl_create_secret_tls/) named deltafi-example-com.

This will configure the web UI to be available at deltafi.example.com and to display "Example DeltaFi" as the site title.

> For a detailed explanation of the values that can be set this way, see the [Configuration](/configuration) section.

Now run the install command passing it the site-specific values file we just created.

```
deltafi install --values ~/site.values.yaml
```

This process can take up to 10 minutes. If it succeeds, the output should look something like this:

```
>> 2022-07-08T22:09:07Z Resolving helm dependencies
...
>> 2022-07-08T22:10:34Z Installing DeltaFi
Release "deltafi" has been upgraded. Happy Helming!
NAME: deltafi
LAST DEPLOYED: Fri Jul  8 22:17:11 2022
NAMESPACE: deltafi
STATUS: deployed
REVISION: 1
>> 2022-07-08T22:17:23Z Loading core flow plans
>> 2022-07-08T22:17:23Z Successfully loaded flow plan error from file error-flowplan.json
>> 2022-07-08T22:17:23Z DeltaFi install complete
```

### Verification

You can verify the install was successful by running the status command:

```
deltafi status
```

If the system is healthy, the output should look like this:

```
System State: Healthy

 ✔ Action Queue Check
 ✔ Kubernetes Deployment Check
 ✔ Kubernetes Ingress Check
 ✔ Kubernetes Pod Check
 ✔ Kubernetes Service Check
 ✔ Kubernetes Stateful Set Check
 ✔ Kubernetes Storage Check
```

If not, the output should give you an indication of what is wrong and how to fix it.

### Post Install

#### Set Admin Password

The default user for the web UI is `admin`. You can set the password with the following command:

```
 deltafi set-admin-password
```
