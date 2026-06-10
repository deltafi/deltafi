{{/*
  Annotations for authentication
*/}}
{{- define "authAnnotations" -}}
{{- if eq .Values.deltafi.auth.mode "basic" }}
{{- include "basicAuthAnnotations" .}}
{{- else if eq .Values.deltafi.auth.mode "cert" }}
{{- include "certAuthAnnotations" .}}
{{- else }}
{{- include "noAuthAnnotations" .}}
{{- end }}
nginx.ingress.kubernetes.io/auth-cache-duration: 200 1m
nginx.ingress.kubernetes.io/auth-response-headers: Authorization, X-User-ID, X-User-Name, X-User-Permissions, X-Metrics-Role
nginx.ingress.kubernetes.io/auth-url: http://deltafi-core-service.deltafi.svc.cluster.local/api/v2/auth
{{- end -}}

{{- define "noAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-cache-key: no-auth
{{- end -}}

{{- define "basicAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-realm: "DeltaFi Auth"
nginx.ingress.kubernetes.io/auth-cache-key: $remote_user$http_authorization
{{- end -}}

{{- define "certAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-tls-verify-client: "on"
nginx.ingress.kubernetes.io/auth-tls-secret: {{ .Release.Namespace }}/{{ .Values.deltafi.auth.secret }}
nginx.ingress.kubernetes.io/auth-tls-verify-depth: "2"
nginx.ingress.kubernetes.io/auth-cache-key: $ssl_client_s_dn$http_authorization
{{- end -}}

{{- define "defaultIngressAnnotations" -}}
{{- if .Values.ingress.tls.ssl_ciphers }}
nginx.ingress.kubernetes.io/ssl-ciphers: {{ .Values.ingress.tls.ssl_ciphers }}
{{- end -}}
{{- end -}}

{{- define "defaultStartupProbe" -}}
startupProbe:
  exec:
    command: ["/probe.sh"]
  periodSeconds: 3
  timeoutSeconds: 10
  failureThreshold: 100
{{- end -}}

{{- define "relaxedStartupProbe" -}}
startupProbe:
  exec:
    command: ["/probe.sh"]
  periodSeconds: 5
  timeoutSeconds: 10
  failureThreshold: 5000
{{- end -}}

{{- define "defaultReadinessProbe" -}}
readinessProbe:
  exec:
    command: ["/probe.sh"]
  periodSeconds: 20
  timeoutSeconds: 10
  failureThreshold: 4
{{- end -}}

{{- define "defaultLivenessProbe" -}}
livenessProbe:
  exec:
    command: ["/probe.sh"]
  periodSeconds: 30
  timeoutSeconds: 30
  failureThreshold: 2
{{- end -}}

{{- define "actionStartupProbe" -}}
startupProbe:
  exec:
    command:
    - cat
    - /tmp/running
  initialDelaySeconds: 5
  periodSeconds: 3
  timeoutSeconds: 1
  failureThreshold: 30
{{- end -}}

{{- define "initContainersWaitForCore" -}}
initContainers:
- name: wait-for-core
  image: busybox:1.36.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-core-service 80 && echo deltafi-core ok;
      do sleep 1;
    done
{{- end -}}

{{/*
  Returns true if metrics (victoriametrics) is enabled.
  Checks deltafi.metrics.enabled (preferred) and the deprecated enable.victoriametrics flag.
*/}}
{{- define "metricsEnabled" -}}
{{- $metricsEnabled := true -}}
{{- if .Values.deltafi.metrics -}}
  {{- if hasKey .Values.deltafi.metrics "enabled" -}}
    {{- $metricsEnabled = .Values.deltafi.metrics.enabled -}}
  {{- end -}}
{{- end -}}
{{- if and .Values.enable.victoriametrics $metricsEnabled -}}true{{- end -}}
{{- end -}}

{{/*
  Returns true if dashboards (grafana) is enabled.
  Checks deltafi.dashboards.enabled (preferred) and the deprecated enable.grafana flag.
*/}}
{{- define "dashboardsEnabled" -}}
{{- $dashboardsEnabled := true -}}
{{- if .Values.deltafi.dashboards -}}
  {{- if hasKey .Values.deltafi.dashboards "enabled" -}}
    {{- $dashboardsEnabled = .Values.deltafi.dashboards.enabled -}}
  {{- end -}}
{{- end -}}
{{- if and .Values.enable.grafana $dashboardsEnabled (or .Values.tags.metrics .Values.tags.logs) -}}true{{- end -}}
{{- end -}}

{{- define "initContainersWaitForGraphite" -}}
initContainers:
- name: wait-for-victoriametrics
  image: busybox:1.36.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-victoriametrics 2003 && echo victoriametrics ok;
      do sleep 1;
    done
{{- end -}}

{{- define "initContainersWaitForDatabases" -}}
initContainers:
- name: wait-for-postgres
  image: postgres:{{ .Values.postgres.version }}-alpine3.22
  env:
  - name: PGUSER
    value: deltafi
  - name: PGPASSWORD
    valueFrom:
      secretKeyRef:
        name: deltafi.deltafi-postgres
        key: password
  command:
    - 'sh'
    - '-c'
    - >
      until PGPASSWORD=$PGPASSWORD psql -h deltafi-postgres -U $PGUSER -d deltafi -c '\q' && echo PostgreSQL is up and ready;
        do sleep 1;
      done
{{- end -}}

{{- define "globalEnvVars" -}}
{{- range .Values.deltafi.globalEnv }}
- {{ toYaml . | nindent 2 | trim }}
{{- end }}
{{- end -}}

{{- define "pluginEnvVars" -}}
{{- range .Values.deltafi.pluginEnv }}
- name: {{ .name }}
  value: {{ .value | quote }}
{{- end }}
{{- end -}}

{{- define "defaultEnvVars" -}}
- name: NODE_NAME
  valueFrom:
    fieldRef:
      fieldPath: spec.nodeName
{{- end -}}

{{/*
  Returns true if local object storage is enabled.
  Checks both enable.local_object_storage and the deprecated enable.minio flag.
*/}}
{{- define "localObjectStorageEnabled" -}}
{{- if or .Values.enable.local_object_storage .Values.enable.minio -}}true{{- end -}}
{{- end -}}

{{/*
  Returns true if local artifact storage is enabled.
*/}}
{{- define "localArtifactStorageEnabled" -}}
{{- if .Values.enable.local_artifact_storage -}}true{{- end -}}
{{- end -}}

{{- define "commonEnvVars" -}}
- name: CORE_URL
  value: http://deltafi-core-service
- name: MINIO_URL
  value: {{ .Values.deltafi.storage.url | default "http://deltafi-s3proxy:9000" }}
- name: MINIO_PARTSIZE
  value: "5242880"
- name: REDIS_URL
  value: http://deltafi-valkey-master:6379
- name: REDIS_HOST
  value: deltafi-redis-master
- name: REDIS_PORT
  value: "6379"
- name: STORAGE_BUCKET_NAME
  value: {{ .Values.deltafi.storage.bucketName | default "storage" }}
- name: STORAGE_INMEMORY
  value: {{ .Values.deltafi.storage.inMemory | default false | quote }}
# Deprecated: SNOWBALL_ENABLED is only for backward compatibility with older plugins
- name: SNOWBALL_ENABLED
  value: "false"
- name: VALKEY_URL
  value: http://deltafi-valkey-master:6379
- name: DELTAFI_UI_DOMAIN
  value: {{ .Values.ingress.domain }}
- name: AUTH_MODE
  value: {{ .Values.deltafi.auth.mode | quote }}
- name: LOCAL_STORAGE_ARTIFACTS
  value: {{ include "localArtifactStorageEnabled" . | quote }}
- name: ARTIFACT_STORAGE_URL
  value: {{ .Values.deltafi.artifactStorage.url | quote }}
- name: ARTIFACT_STORAGE_BUCKET_NAME
  value: {{ .Values.deltafi.artifactStorage.bucketName | quote }}
{{- end -}}

{{- define "valkeyEnvVars" -}}
- name: VALKEY_PASSWORD
  valueFrom:
    secretKeyRef:
      name: valkey-password
      key: valkey-password
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: valkey-password
      key: valkey-password
{{- end -}}

{{- define "artifactStorageEnvVars" -}}
{{- if .Values.deltafi.artifactStorage.secret }}
- name: ARTIFACT_STORAGE_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.deltafi.artifactStorage.secret }}
      key: accessKey
- name: ARTIFACT_STORAGE_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.deltafi.artifactStorage.secret }}
      key: secretKey
{{- end }}
{{- end -}}

{{- define "minioEnvVars" -}}
- name: MINIO_ACCESSKEY
  value: deltafi
- name: MINIO_SECRETKEY
  valueFrom:
    secretKeyRef:
      name: minio-keys
      key: rootPassword
{{- end -}}

{{- define "postgresEnvVars" -}}
- name: POSTGRES_USER
  value: deltafi
- name: POSTGRES_DB
  value: deltafi
- name: POSTGRES_PASSWORD
  valueFrom:
    secretKeyRef:
      name: deltafi.deltafi-postgres
      key: password
{{ if .Values.deltafi.lookup.enabled }}
- name: POSTGRES_LOOKUP_PASSWORD
  valueFrom:
    secretKeyRef:
      name: deltafi.deltafi-postgres-lookup
      key: password
{{ end }}
{{- end -}}

{{- define "graphiteEnvVars" -}}
- name: GRAPHITE_HOST
  value: deltafi-victoriametrics
- name: GRAPHITE_PORT
  value: "2003"
{{- end -}}

{{- define "metricsEnabledEnvVar" -}}
- name: METRICS_ENABLED
  value: {{ if include "metricsEnabled" . }}"true"{{ else }}"false"{{ end }}
{{- end -}}

{{- define "sslEnvVars" -}}
{{- $secretName := .secretName | default .Values.deltafi.core.ssl.secret -}}
- name: KEY_PASSWORD
  valueFrom:
    secretKeyRef:
      name:  {{ $secretName }}
      key: keyPassword
      optional: true
- name: SSL_PROTOCOL
  valueFrom:
    secretKeyRef:
      name:  {{ $secretName }}
      key: sslProtocol
      optional: true
{{- end -}}

{{- define "sslVolumeMount" -}}
{{- $volumeName := .volumeName | default "ssl-volume" -}}
- name: {{ $volumeName }}
  mountPath: /certs
  readOnly: true
{{- end -}}

{{- define "entityResolverSslVolume" -}}
{{- include "sslVolume" (merge (dict "secretName" (coalesce .Values.deltafi.ssl.secret .Values.deltafi.auth.entityResolver.ssl.secret) "volumeName" "entity-resolver-ssl-volume") .) -}}
{{- end -}}

{{- define "entityAuthorizerSslVolume" -}}
{{- include "sslVolume" (merge (dict "secretName" (coalesce .Values.deltafi.ssl.secret .Values.deltafi.auth.entityAuthorizer.ssl.secret) "volumeName" "entity-authorizer-ssl-volume") .) -}}
{{- end -}}

{{- define "pluginSslVolume" -}}
{{- include "sslVolume" (merge (dict "secretName" (coalesce .Values.deltafi.ssl.secret .Values.deltafi.plugins.ssl.secret "ssl-secret")) .) -}}
{{- end -}}

{{- define "sslVolume" -}}
{{ $volumeName := .volumeName | default "ssl-volume" -}}
{{ $secretName := .secretName | default .Values.deltafi.core.ssl.secret -}}
- name: {{ $volumeName }}
  projected:
    sources:
      - secret:
          name: {{ .Values.deltafi.auth.secret | default "auth-secret"}}
          optional: true
      - secret:
          name: {{ $secretName }}
          optional: true
{{- end -}}

{{- define "actionContainerSpec" -}}
env:
  - name: ACTIONS_HOSTNAME
    valueFrom:
      fieldRef:
        fieldPath: spec.nodeName
  - name: APP_NAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: SPRING_PROFILES_ACTIVE
    value: kubernetes
{{- include "commonEnvVars" . | nindent 2 }}
{{- include "valkeyEnvVars" . | nindent 2 }}
{{- include "minioEnvVars" . | nindent 2 }}
{{- include "sslEnvVars" (merge (dict "secretName" .Values.deltafi.plugins.ssl.secret) .) | nindent 2 }}
{{- include "pluginEnvVars" . | nindent 2 }}
{{ include "actionStartupProbe" . }}
volumeMounts:
{{- include "sslVolumeMount" . | nindent 2 }}
{{- end -}}

{{- define "coreEnvVars" -}}
- name: METRICS_PERIOD_SECONDS
  value: "10"
- name: SERVER_PORT
  value: "9292"
{{ include "commonEnvVars" . }}
{{ include "globalEnvVars" . }}
{{ include "postgresEnvVars" . }}
{{ include "minioEnvVars" . }}
{{ include "artifactStorageEnvVars" . }}
{{ include "valkeyEnvVars" . }}
{{ include "sslEnvVars" . }}
- name: DELTAFI_SECRET_CA_CHAIN
  value:  {{ coalesce .Values.deltafi.ssl.secret .Values.deltafi.auth.secret "auth-secret" }}
- name: DELTAFI_SECRET_ENTITY_RESOLVER_SSL
  value: {{ coalesce .Values.deltafi.ssl.secret .Values.deltafi.auth.entityResolver.ssl.secret "ssl-secret" }}
- name: DELTAFI_SECRET_INGRESS_SSL
  value: {{ coalesce .Values.deltafi.ssl.secret .Values.ingress.tls.secrets.default "ssl-secret" }}
- name: DELTAFI_SECRET_CORE_SSL
  value: {{ coalesce .Values.deltafi.ssl.secret .Values.deltafi.core.ssl.secret "ssl-secret" }}
- name: DELTAFI_SECRET_PLUGINS_SSL
  value: {{ coalesce .Values.deltafi.ssl.secret .Values.deltafi.plugins.ssl.secret "ssl-secret" }}
{{ if .Values.deltafi.auth.entityResolver.enabled }}
- name: ENTITY_RESOLVER_ENABLED
  value: "true"
- name: ENTITY_RESOLVER_URL
  value: {{ .Values.deltafi.auth.entityResolver.url | default "http://127.0.0.1:8080/" }}
{{ end }}
{{ if .Values.deltafi.auth.entityAuthorizer.enabled }}
- name: ENTITY_AUTHORIZER_ENABLED
  value: "true"
- name: ENTITY_AUTHORIZER_URL
  value: {{ .Values.deltafi.auth.entityAuthorizer.url | default "http://127.0.0.1:8081/" }}
{{ end }}
- name: GRAFANA_ENABLED
  value: {{ if include "dashboardsEnabled" . }}"true"{{ else }}"false"{{ end }}
- name: METRICS_ENABLED
  value: {{ if include "metricsEnabled" . }}"true"{{ else }}"false"{{ end }}
- name: ANALYTICS_ENABLED
  value: {{ .Values.deltafi.analytics.enabled | quote }}
- name: HEADLAMP_TOKEN
  valueFrom:
    secretKeyRef:
      name: deltafi-headlamp-secret
      key: token
{{- end }}

{{- define "coreVolumeMounts" -}}
{{- include "sslVolumeMount" . }}
- mountPath: /template
  name: action-deployment-template
  readOnly: true
- mountPath: /config/plugin.env
  name: plugin-env-volume
  subPath: plugin.env
  readOnly: true
{{- end -}}

{{- define "coreVolumes" -}}
{{- include "sslVolume" . }}
{{ include "entityResolverSslVolume" . }}
{{ include "entityAuthorizerSslVolume" . }}
- name: action-deployment-template
  configMap:
    name: deltafi-action-deployment
- name: plugin-env-volume
  configMap:
    name: deltafi-plugin-env
    optional: true
- name: entity-resolver-config
  configMap:
    name: entity-resolver-config
    optional: true
- name: entity-authorizer-config
  configMap:
    name: entity-authorizer-config
    optional: true
{{- end -}}

{{- define "entityResolverContainer" -}}
- name: deltafi-entity-resolver
  image: {{ .Values.deltafi.auth.entityResolver.image }}
  volumeMounts:
  {{- include "sslVolumeMount" (dict "volumeName" "entity-resolver-ssl-volume") | nindent 2 }}
  - name: entity-resolver-config
    mountPath: /config
    readOnly: true
  env:
  {{- include "sslEnvVars" (merge (dict "secretName" .Values.deltafi.auth.entityResolver.ssl.secret) .) | nindent 2 }}
  - name: DATA_DIR
    value: /config
{{- end -}}

{{- define "entityAuthorizerContainer" -}}
- name: deltafi-entity-authorizer
  image: {{ .Values.deltafi.auth.entityAuthorizer.image }}
  volumeMounts:
  {{- include "sslVolumeMount" (dict "volumeName" "entity-authorizer-ssl-volume") | nindent 2 }}
  - name: entity-authorizer-config
    mountPath: /config
    readOnly: true
  env:
  {{- include "sslEnvVars" (merge (dict "secretName" .Values.deltafi.auth.entityAuthorizer.ssl.secret) .) | nindent 2 }}
  - name: DATA_DIR
    value: /config
{{- end -}}