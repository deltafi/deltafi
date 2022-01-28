### Install / Upgrade

    helm dependency update
    helm upgrade deltafi . -n deltafi --create-namespace --install

### Uninstall

    helm uninstall deltafi -n deltafi


### Add External Properties to the Config Server

#### Read properties from the filesystem

The config server will look for external properties to serve to the clients in the
`/config` directory of the running container. This is useful if you want to customize
properties prior to startup instead of relying on changing the defaults in MongoDB.


##### Mount a ConfigMap
In this example you would create a ConfigMap which is mounted to `/config`. The
keys are the name of your yaml files followed by the yaml content.

config_server section in the values.yaml:

```yaml
  config_server:
    image: deltafi-config-server:tag
    envVars:
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
    volumes:
      - name: native-config-properties
        configMap:
          name: config-server-configmap
    volumeMounts:
      - name: native-config-properties
        mountPath: /config
```

Sample ConfigMap resource:

```yaml
kind: ConfigMap 
apiVersion: v1 
metadata:
  name: config-server-configmap 
data:
  deltafi-common.yaml: |-
    deltafi:
      requeueSeconds: 12
      deltaFileTtl: 5d
  action-kit.yaml: |-
    actions:
      action-polling-initial-delay-ms: 4000
      action-polling-period-ms: 200
      action-registration-initial-delay-ms: 500
      action-registration-period-ms: 15000
```

##### Mount a PV

In the following example you would put your custom yaml files in `/data/config-server/`
and mount them in `/config` for the config-server to read.

config_server section in the values.yaml:

```yaml
  config_server:
    image: deltafi-config-server:tag
    envVars:
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
    volumes:
      - name: native-config
        hostPath:
          path: /data/config-server/
          type: DirectoryOrCreate
    volumeMounts:
      - name: native-config
        mountPath: /config
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