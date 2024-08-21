# Install DeltaFi Core

In this section, we will install the DeltaFi Core.

## Bootstrap Kubernetes

Before installing the DeltaFi Core, we need to bootstrap the Kubernetes cluster. This process includes creating Kubernetes storage volumes and secrets.

Run the `bootstrap-deltafi.yml` playbook passing in the inventory file you created in the [Ansible Setup](/install/ansible#inventory-file) section.

```
cd ~/ansible
ansible-playbook bootstrap-deltafi.yml -i inventory/site
```

## Install Core

Clone or extract the DeltaFi Core git repository into your home directory.

```
cd ~
git clone git@gitlab.com:deltafi/deltafi.git
```

Next, we’ll install the DeltaFi command-line interface ([CLI](/operating/CLI)). This provides the `deltafi` command - the primary way of interacting with the DeltaFi system. Use the following command to install the DeltaFi CLI.

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

## Verification

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

## Post Install

### Set Admin Password

The default user for the web UI is `admin`. You can set the password with the following command:

```
 deltafi set-admin-password
```
