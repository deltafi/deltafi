# DeltaFi Plugin - {{artifactId}}

{{description}}

Install Steps:

1. Build and publish the image (`./gradlew dockerPushLocal` if you are using the org.deltafi.plugin-convention convention)
2. Run the following (pass the image created above as the argument for the install)

```bash
deltafi install-plugin deltafi/{{artifactId}}:latest
```

Local KinD Setup

1. Install a DeltaFi KinD Cluster
2. Update the `cluster.yaml` `plugins` section to include the following:
```yaml
plugins:
  - name: {{artifactId}}
    url: 'git@<plugin-git-repo>'
```
