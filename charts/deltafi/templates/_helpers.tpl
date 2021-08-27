{{/*
  Annotations for authentication
*/}}
{{- define "authAnnotations" -}}
{{- if eq .Values.deltafi.auth.mode "basic" }}
{{- include "basicAuthAnnotations" .}}
{{- else if eq .Values.deltafi.auth.mode "cert" }}
{{- include "certAuthAnnotations" .}}
{{- end }}
{{- end -}}

{{- define "basicAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-realm: "DeltaFi Auth"
nginx.ingress.kubernetes.io/auth-secret: {{ .Release.Namespace }}/{{ .Values.deltafi.auth.secret }}
nginx.ingress.kubernetes.io/auth-type: basic
{{- end -}}

{{- define "certAuthAnnotations" -}}
nginx.ingress.kubernetes.io/auth-tls-verify-client: "yes"
nginx.ingress.kubernetes.io/auth-tls-secret: {{ .Release.Namespace }}/{{ .Values.deltafi.auth.secret }}
nginx.ingress.kubernetes.io/auth-tls-verify-depth: "2"
nginx.ingress.kubernetes.io/auth-url: http://deltafi-auth-service.deltafi.svc.cluster.local/auth
nginx.ingress.kubernetes.io/auth-cache-key: $remote_user$http_authorization
nginx.ingress.kubernetes.io/auth-cache-duration: 200 1m, 401 1m
{{- end -}}
