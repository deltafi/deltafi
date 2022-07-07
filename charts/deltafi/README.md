### Install / Upgrade

    helm dependency update
    helm upgrade deltafi . -n deltafi --create-namespace --install

### Uninstall

    helm uninstall deltafi -n deltafi

### Setup SSL in deltafi-core-actions

1. Create a secret holding the keyStore and trustStore. By default, the deltafi-core-actions will look for a secret named `keystore-secret`, and the action-kit will look for `/etc/pki/keyStore.p12` and `/etc/pki/trustStore.jks`.
   1. `kubectl create secret generic keystore-secret --from-file=keyStore.p12 --from-file=trustStore.jks`
1. Create a secret holding the passwords for the keyStore and trustStore. By default, the deltafi-core-actions deployment will look for a secret named `keystore-password-secret`. The key names are used as the environment variable name and must be KEYSTORE_PASSWORD and TRUSTSTORE_PASSWORD.
   1. `kubectl create secret generic keystore-password-secret --from-literal=KEYSTORE_PASSWORD=somevalue --from-literal=TRUSTSTORE_PASSWORD=somevalue`

### Add External Properties to the Config Server

#### Read properties from the filesystem

The config server will look for external properties to serve to the clients in the
`/config` directory of the running container. This is useful if you want to customize
properties prior to startup instead of relying on changing the defaults in MongoDB.


##### Mount a ConfigMap
By default, the config-server will look for a ConfigMap named `config-server-configmap`
and mount that to `/config`. To add additional properties or to override read only properties
create this ConfigMap with nested yaml, where the data.key will be the fileName. A key of `deltafi-common.yaml`
will add properties for all applications, a key of `action-kit.yaml` will add properties for all plugins. Any
other key should match the name of a specific application running in a plugin (i.e. `stix-actions.yaml`)
to apply the properties to that specific application.

```yaml
kind: ConfigMap 
apiVersion: v1 
metadata:
  name: config-server-configmap 
data:
  deltafi-common.yaml: |-
    deltafi:
      delete:
        onCompletion: false
        frequency: PT1M
  action-kit.yaml: |-
    actions:
      action-polling-initial-delay-ms: 4000
      action-polling-period-ms: 200
      action-registration-initial-delay-ms: 500
      action-registration-period-ms: 15000
```

#### Read properties from git
To change the config-server to read from a git repo, you must set the SPRING_PROFILES_ACTIVE
environment variable and set up the environment variables necessary for authentication (see deltafi-config-server/README.md for all options).

The following example reads from https://gitlab.com/systolic/deltafi/deltafi-config.git using
token based authentication.

```yaml
config_server:
    image: deltafi-config-server:0.17.0-SNAPSHOT
    envVars:
      - name: SPRING_PROFILES_ACTIVE
        value: git
      - name: GIT_CONFIG_REPO
        value: https://gitlab.com/systolic/deltafi/deltafi-config.git
      - name: USERNAME
        value: config-server
      - name: DEFAULT_LABEL
        value: main
      - name: CLONE_ON_START
        value: "true"
      - name: MONGO_HOST
        value: deltafi-mongodb
      - name: MONGO_AUTH_DB
        value: deltafi
      - name: CONFIG_TOKEN
        valueFrom:
          secretKeyRef:
            name: config-repo-secret
            key: config-token
      - name: MONGO_PASSWORD
        valueFrom:
          secretKeyRef:
            name: mongodb-passwords
            key: mongodb-password
    volumes: []
    volumeMounts: []
```
