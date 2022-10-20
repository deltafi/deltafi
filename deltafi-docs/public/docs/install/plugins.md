# Install Plugins

DeltaFi plugins provide actions and flows for the system. For information on creating plugins, see the [Plugins](/plugins) section.

In this guide we will be installing the Passthrough plugin as an example. Other plugins can be installed using the `deltafi install-plugin` command.


## Configure Plugin Image Repositories
DeltaFi will use `docker.io/deltafi/${artifactId}` as the default plugin image repository with a tag of `${version}`, where `artifactId` and `version` come from the plugin coordinates when installing a plugin. The default repository and pull secret can be changed in the `System Properties` page (`deltafi.plugins.imageRepositoryBase` and `deltafi.plugins.imagePullSecret` properties respectively).

Additional image repositories can be configured for groups of plugins. The following example uses `registry.gitlab.com/systolic/deltafi/deltafi-stix:${artifactId}` as the image repository for plugins in the `org.deltafi.stix` group and `docker-secret` will be used as the imagePullSecret.

```bash
# Create the image repository config file to load into DeltaFi
cat <<EOF > image-repo-config.json
{
  "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/deltafi-stix",
  "pluginGroupIds": ["org.deltafi.stix"],
  "imagePullSecret": "docker-secret"
}
EOF

# Load the config into DeltaFi
deltafi plugin-image-repo save image-repo-config.json
```

> **_NOTE:_**  The plugin image repository configuration can be applied to more than one group of plugins.

### Install command
Install the plugin using the `deltafi` command.

```
deltafi install-plugin "groupId:artifactId:version"
```

If it succeeds, the output should look something like this:

```
>> Successfully installed plugin "groupId:artifactId:version"
```

The `deltafi install-plugin` command supports the following flags to override the configured image repository, pull secret, or extra containers.
```
--image-repository-base    Image repository base to use instead of using configured base repository
--pull-secret              Pull secret to use instead of using the configured pull secret
--deployment-extras-file   Path to a file containing extra containers to include in the deployment"
```

## Advanced Deployment

### Custom Deployments (sidecars)

DeltaFi can be configured to read and apply deployment customizations when generating a plugins' deployment resource. Currently, DeltaFi supports adding additional containers to the standard plugin deployment. In the following example, DeltaFi is configured to read the extra containers needed to run the `deltafi-stix-actions` plugin from a GitLab repository.

Sample: Extra containers required to run the `deltafi-stix-actions` plugin:
```yaml
extraContainers:
  - name: stix-conversion-server
    image: deltafi/stix-conversion-server:0.0.1
```

The following steps are used to configure DeltaFi for installing the `deltafi-stix-actions` plugin.
```bash
# Create the secret holding the credentials needed to pull the deployment-extras.yaml file
kubectl create secret generic stix-repo-secret --from-literal=username=${TOKEN_NAME} --from-literal=password=${TOKEN_VALUE} --namespace deltafi

# Create the config file to load into DeltaFi
cat <<EOF > deployment-config.json
{
    "groupId": "org.deltafi.stix",
    "artifactId": "deltafi-stix-actions",
    "urlTemplate": "https://gitlab.com/api/v4/projects/26239627/packages/generic/deltafi-stix/0.0.1/deployment-extras.yaml",
    "secretName": "stix-repo-secret"
}
EOF

# Load the config into DeltaFi
deltafi plugin-customization save deployment-config.json
```

> **_NOTE:_**  The URL template supports ${groupId}, ${artifactId}, and ${version} placeholders that are populated from the plugin coordinates when installing the plugin.