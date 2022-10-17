# Install Kubernetes

DeltaFi is designed to run in a Kubernetes cluster. In this section, we will set up a new Kubernetes cluster using an Ansible playbook included with DeltaFi.

<Note>

If you already have a Kubernetes cluster provisioned and would like to use that for installing DeltaFi, please skip to the [Install DeltaFi Core](/install/core) section of this guide.

</Note>

## Air-Gap Install

If you are installing in an air-gapped environment (not connected to the internet), please create a directory called `/data/rke2`:

```bash
sudo mkdir -p /data/rke2
sudo chown $USER:$USER /data/rke2
```

Place the following RKE2 files in `/data/rke2`:

- `rke2-images.linux-amd64.tar.zst`
- `rke2.linux-amd64.tar.gz`
- `sha256sum-amd64.txt`

These files can be obtained [here](https://github.com/rancher/rke2/releases).

Also, place the `helm` binary in `/data/rke2`. It can be obtained [here](https://github.com/helm/helm/releases).

Next, run the `install-rke2.yaml` playbook passing in the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section and setting `rke2_airgap_mode` to `true`.

```bash
cd ~/ansible
ansible-playbook install-rke2.yml -i inventory/site -e rke2_airgap_mode=true
```

## Online Install

If you are connected to the internet, simply run the `install-rke2.yml` playbook passing it the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section.

```bash
cd ~/ansible
ansible-playbook install-rke2.yml -i inventory/site
```

## Verification

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
df-test-01   Ready    control-plane,etcd,master   1h    v1.20.15+rke2r2
```
