# DeltaFi Plugin - {{artifactId}}

{{description}}

Install Steps:

1. Add the image repository for the {{groupId}} or set the default system property to point to the image repository where this image will be published
2. Run the following

```bash
deltafi install-plugin {{groupId}}:{{artifactId}}:<IMAGE-TAG>
```

Local KinD Setup

1. Install a DeltaFi KinD Cluster
2. Update the `cluster.yaml` `plugins` section to include the following:
```yaml
plugins:
  - name: {{artifactId}}
    plugin_coordinates: "{{groupId}}:{{artifactId}}:latest"
    image_repository_base: "localhost:5000/"
    url: 'git@<plugin-git-repo>'
```