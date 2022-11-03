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
nginx.ingress.kubernetes.io/auth-cache-duration: 200 5m
nginx.ingress.kubernetes.io/auth-response-headers: X-User-ID, X-User-Name, X-User-Permissions
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
nginx.ingress.kubernetes.io/auth-tls-verify-client: "yes"
nginx.ingress.kubernetes.io/auth-tls-secret: {{ .Release.Namespace }}/{{ .Values.deltafi.auth.secret }}
nginx.ingress.kubernetes.io/auth-tls-verify-depth: "2"
nginx.ingress.kubernetes.io/auth-url: http://deltafi-auth-service.deltafi.svc.cluster.local/cert-auth
nginx.ingress.kubernetes.io/auth-cache-key: $ssl_client_s_dn$http_authorization
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

{{- define "initContainersWaitForCore" -}}
initContainers:
- name: wait-for-core
  image: busybox:1.35.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-core-service 80 && echo deltafi-core ok;
      do sleep 1;
    done
{{- end -}}

{{- define "initContainersWaitForMongo" -}}
initContainers:
- name: wait-for-mongo
  image: busybox:1.35.0
  command:
  - 'sh'
  - '-c'
  - >
    until nc -z -w 2 deltafi-mongodb 27017 && echo mongodb ok;
      do sleep 1;
    done
{{- end -}}

{{- define "defaultEnvVars" -}}
- name: NODE_NAME
  valueFrom:
    fieldRef:
      fieldPath: spec.nodeName
{{- end -}}

{{- define "keyStorePasswordSecret"  -}}
- secretRef:
    name: {{ .Values.deltafi.keyStore.passwordSecret }}
    optional: true
{{- end -}}

{{- define "keyVolumeMounts" -}}
- mountPath: "{{ .Values.deltafi.keyStore.mountPath }}/{{ .Values.deltafi.keyStore.keyStoreName }}"
  name: keystore
  readOnly: true
  subPath: {{ .Values.deltafi.keyStore.keyStoreName }}
- mountPath: "{{ .Values.deltafi.keyStore.mountPath }}/{{ .Values.deltafi.keyStore.trustStoreName }}"
  name: keystore
  readOnly: true
  subPath: {{ .Values.deltafi.keyStore.trustStoreName }}
{{- end -}}

{{- define "keyVolumes" -}}
- name: keystore
  secret:
    secretName: {{ .Values.deltafi.keyStore.secret }}
    optional: true
{{- end -}}

{{- define "actionContainerSpec" -}}
env:
{{- with .Values.deltafi.actions.envVars }}
{{- toYaml . | nindent 2 }}
{{- end }}
{{- with .Values.deltafi.envVars }}
{{- toYaml . | nindent 2 }}
{{- end }}
envFrom:
{{- include "keyStorePasswordSecret" . | nindent 2 }}
volumeMounts:
{{- include "keyVolumeMounts" . | nindent 2 }}
{{- end -}}

{{- define "coreEnvVars" -}}
{{- with .Values.deltafi.envVars }}
{{- toYaml . }}
{{ end }}
{{- with .Values.deltafi.core.envVars }}
{{- toYaml . }}
{{- end }}
{{- end -}}

{{- define "coreVolumeMounts" -}}
{{- include "keyVolumeMounts" . }}
- mountPath: {{ .Values.deltafi.core.nativeConfigMountPath }}
  name: config-map
  readOnly: true
- mountPath: /template
  name: action-deployment-template
  readOnly: true
{{- end -}}

{{- define "coreVolumes" -}}
{{- include "keyVolumes" . }}
- name: config-map
  configMap:
    name: {{ .Values.deltafi.core.nativeConfigMap }}
    optional: true
- name: action-deployment-template
  configMap:
    name: deltafi-action-deployment
{{- end -}}