# Ansible Setup

The installation process for DeltaFi is handled by Ansible playbooks. Clone or extract the DeltaFi Ansible git repository into your home directory and install the dependencies by using the following commands:

```bash
cd ~
git clone git@gitlab.com:deltafi/ansible.git
cd ~/ansible
ansible-galaxy install -r requirements.yml
ansible-galaxy collection install -r requirements.yml
```

## Inventory File

The DeltaFi Ansible Playbooks act on an inventory file. This file describes your installation environment and instructs Ansible where to install what. The DeltaFi Ansible repository that you cloned/extracted in the previous step includes a few example inventory files in the `inventory` directory in the root of the repository. Copy one to a new file called `site` and modify it to include your host information and what roles you want each host to have.

- For a single-node example, see `inventory/test`
- For a multi-node example, see `inventory/dev`

For the purposes of this guide, we’ll assume an inventory file named `site`.

## Node roles

When looking at the example inventory files, notice the `deltafi_node_roles` field specified for each server. DeltaFi must have one or more `storage` nodes and one or more `compute` nodes. A node can have both roles, as is the case with the single-node example.
