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
nginx.ingress.kubernetes.io/auth-response-headers: X-User-ID, X-User-Name, X-User-Permissions, X-Metrics-Role
{{- end -}}

{{- define "noAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-url: http://deltafi-auth-service.deltafi.svc.cluster.local/no-auth
nginx.ingress.kubernetes.io/auth-cache-key: no-auth
{{- end -}}

{{- define "basicAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-realm: "DeltaFi Auth"
nginx.ingress.kubernetes.io/auth-url: http://deltafi-auth-service.deltafi.svc.cluster.local/basic-auth
nginx.ingress.kubernetes.io/auth-cache-key: $remote_user$http_authorization
{{- end -}}

{{- define "certAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-tls-verify-client: "on"
nginx.ingress.kubernetes.io/auth-tls-secret: {{ .Release.Namespace }}/{{ .Values.deltafi.auth.secret }}
nginx.ingress.kubernetes.io/auth-tls-verify-depth: "2"
nginx.ingress.kubernetes.io/auth-url: http://deltafi-auth-service.deltafi.svc.cluster.local/cert-auth
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
  failureThreshold: 30
{{- end -}}

{{- define "defaultReadinessProbe" -}}
readinessProbe:
  exec:
    command: ["/probe.sh"]
  periodSeconds: 20
  timeoutSeconds: 10
  failureThreshold: 2
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

{{- define "initContainersWaitForGraphite" -}}
initContainers:
- name: wait-for-graphite
  image: busybox:1.36.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-graphite 2003 && echo graphite ok;
      do sleep 1;
    done
{{- end -}}

{{- define "initContainersWaitForDatabases" -}}
initContainers:
- name: wait-for-postgres
  image: postgres:{{ .Values.postgres.version }}-alpine3.20
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
{{- if .Values.clickhouse.enabled }}
- name: wait-for-clickhouse
  image: busybox:1.36.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-clickhouse 9000 && echo clickhouse ok;
      do sleep 1;
    done
{{- end -}}
{{- end -}}

{{- define "defaultEnvVars" -}}
- name: NODE_NAME
  valueFrom:
    fieldRef:
      fieldPath: spec.nodeName
{{- end -}}

{{- define "commonEnvVars" -}}
- name: CORE_URL
  value: http://deltafi-core-service/api/v2
- name: MINIO_URL
  value: http://deltafi-minio:9000
- name: MINIO_PARTSIZE
  value: "5242880"
- name: REDIS_URL
  value: http://deltafi-valkey-master:6379
- name: REDIS_HOST
  value: deltafi-redis-master
- name: REDIS_PORT
  value: "6379"
- name: VALKEY_URL
  value: http://deltafi-valkey-master:6379
- name: DELTAFI_UI_DOMAIN
  value: {{ .Values.ingress.domain }}
- name: AUTH_MODE
  value: {{ .Values.deltafi.auth.mode | quote }}
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
- name: POSTGRES_PASSWORD
  valueFrom:
    secretKeyRef:
      name: deltafi.deltafi-postgres
      key: password
{{- end -}}

{{- define "graphiteEnvVars" -}}
- name: GRAPHITE_HOST
  value: deltafi-graphite
- name: GRAPHITE_PORT
  value: "2003"
{{- end -}}

{{- define "sslEnvVars" -}}
- name: SSL_KEYSTORE
  value: "{{ .Values.deltafi.ssl.mountPath }}/{{ .Values.deltafi.ssl.keyStoreName }}"
- name: SSL_KEYSTORETYPE
  value: {{ .Values.deltafi.ssl.keyStoreType }}
- name: SSL_PROTOCOL
  value: {{ .Values.deltafi.ssl.protocol }}
- name: SSL_TRUSTSTORE
  value: "{{ .Values.deltafi.ssl.mountPath }}/{{ .Values.deltafi.ssl.trustStoreName }}"
- name: SSL_TRUSTSTORETYPE
  value: {{ .Values.deltafi.ssl.trustStoreType }}
{{- end -}}

{{- define "clickhouseEnvVars" -}}
- name: CLICKHOUSE_ENABLED
  value: "{{ .Values.clickhouse.enabled | toString }}"
- name: CLICKHOUSE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: clickhouse-password
      key: clickhouse-password
{{- end -}}

{{- define "keyStorePasswordSecret"  -}}
- secretRef:
    name: {{ .Values.deltafi.ssl.passwordSecret }}
    optional: true
{{- end -}}

{{- define "keyVolumeMounts" -}}
- mountPath: "{{ .Values.deltafi.ssl.mountPath }}/{{ .Values.deltafi.ssl.keyStoreName }}"
  name: keystore
  readOnly: true
  subPath: {{ .Values.deltafi.ssl.keyStoreName }}
- mountPath: "{{ .Values.deltafi.ssl.mountPath }}/{{ .Values.deltafi.ssl.trustStoreName }}"
  name: keystore
  readOnly: true
  subPath: {{ .Values.deltafi.ssl.trustStoreName }}
{{- end -}}

{{- define "keyVolumes" -}}
- name: keystore
  secret:
    secretName: {{ .Values.deltafi.ssl.secret }}
    optional: true
{{- end -}}

{{- define "actionContainerSpec" -}}
env:
  - name: ACTIONS_HOSTNAME
    valueFrom:
      fieldRef:
        fieldPath: spec.nodeName
  - name: SPRING_PROFILES_ACTIVE
    value: kubernetes
{{- include "commonEnvVars" . | nindent 2 }}
{{- include "valkeyEnvVars" . | nindent 2 }}
{{- include "minioEnvVars" . | nindent 2 }}
{{- include "sslEnvVars" . | nindent 2 }}
envFrom:
{{- include "keyStorePasswordSecret" . | nindent 2 }}
{{ include "actionStartupProbe" . }}
volumeMounts:
{{- include "keyVolumeMounts" . | nindent 2 }}
{{- end -}}

{{- define "coreEnvVars" -}}
- name: STATSD_HOSTNAME
  value: "deltafi-graphite"
- name: STATSD_PORT
  value: "8125"
- name: METRICS_PERIOD_SECONDS
  value: "10"
{{ include "commonEnvVars" . }}
{{ include "postgresEnvVars" . }}
{{ include "minioEnvVars" . }}
{{ include "valkeyEnvVars" . }}
{{ include "sslEnvVars" . }}
{{- end }}

{{- define "coreVolumeMounts" -}}
{{- include "keyVolumeMounts" . }}
- mountPath: /template
  name: action-deployment-template
  readOnly: true
{{- end -}}

{{- define "coreVolumes" -}}
{{- include "keyVolumes" . }}
- name: action-deployment-template
  configMap:
    name: deltafi-action-deployment
{{- end -}}
