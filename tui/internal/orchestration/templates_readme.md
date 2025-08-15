## Extending the DeltaFi Chart

The `deltafi up` command installs DeltaFi using Helm under the hood when running in kubernetes.
The helm chart can be extended by creating Helm template files in this directory.
Any templates under this directory will effectively be the same as adding the templates directly to the DeltaFi chart,
giving the templates full access to the `_helpers.tpl` and `values.yaml`.

### Example

The example below creates a new nginx deployment, a config-map for customizing it along with a service and ingress resources to expose it.
Some important notes:
- `hello-ingress.yaml `- this uses the `authAnnotations` named template defined in the main DeltaFi chart to tie into DeltaFi authentication
- `hello-ingress.yaml` - this uses the DeltaFi `ingress` values for TLS configuration and host name settings
- `hello-config-map.yaml` - this is reading the config data from the `site/values.yaml` (or `site/kind.values.yaml` when running in KinD mode)

Steps to run example:
1. Run the bash script found below from the `site/templates` directory
2. Update your `site/values` (`site/kind.values.yaml` for KinD deployments) to include
```yaml
hello:
  title: ~
  body: ~
```
2. Run `deltafi up`
3. Verify your new deployment is accessible (i.e. http://local.deltafi.org/hello-extension)
4. Modify your `site/values.yaml` to customize the page, for example:
```yaml
hello:
  title: Site Extension
  body: |
    <h1>Site Specific Extension!</h1>
    <p>This is a simple web server running in a pod.</p>
    <p>Pod hostname: <span id="hostname"></span></p>
    <script>
      document.getElementById('hostname').textContent = window.location.hostname;
    </script>
```
5. Run `deltafi up` again, restart the deployment to pick up the config-map changes (`kubectl rollout restart deployment hello-web-server`) 
6. Refresh the `hello-extension` page and verify the changes

```bash
#!/usr/bin/env bash

cat << EOF > hello-deployment.yaml
# Deployment - Creates and manages the hello web server pods
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hello-web-server
  labels:
    app: hello-web-server
spec:
  selector:
    matchLabels:
      app: hello-web-server
  template:
    metadata:
      labels:
        app: hello-web-server
    spec:
      containers:
      - name: hello-server
        image: nginx:alpine
        ports:
        - containerPort: 80
        volumeMounts:
        - name: html-volume
          mountPath: /usr/share/nginx/html
        resources:
          requests:
            memory: "64Mi"
            cpu: "250m"
          limits:
            memory: "128Mi"
            cpu: "500m"
      volumes:
      - name: html-volume
        configMap:
          name: hello-html-config
EOF

cat << EOF > hello-config-map.yaml
# ConfigMap - Contains the simple HTML content
apiVersion: v1
kind: ConfigMap
metadata:
  name: hello-html-config
data:
  index.html: |
    <!DOCTYPE html>
    <html>
    <head>
        <title>{{ default "Hello Server" .Values.hello.title }}</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                text-align: center;
                margin-top: 50px;
                background-color: #f0f8ff;
            }
            h1 {
                color: #333;
            }
        </style>
    </head>
    <body>
    {{- if not (empty .Values.hello.body) }}
    {{ .Values.hello.body | indent 4 }}
    {{- else }}
        <h1>Hello from Kubernetes!</h1>
        <p>This is a simple web server running in a pod.</p>
        <p>Pod hostname: <span id="hostname"></span></p>
        <script>
            document.getElementById('hostname').textContent = window.location.hostname;
        </script>
    {{- end }}
    </body>
    </html>

EOF

cat << EOF > hello-service.yaml
# Service - Exposes the deployment within the cluster
apiVersion: v1
kind: Service
metadata:
  name: hello-web-service
  labels:
    app: hello-web-server
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 80
    protocol: TCP
    name: http
  selector:
    app: hello-web-server
EOF

cat << EOF > hello-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hello-web-ingress
  annotations:
  {{- include "defaultIngressAnnotations" . | nindent 4 }}
  {{- include "authAnnotations" . | nindent 4 }}
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  {{- if eq .Values.ingress.tls.enabled true }}
  tls:
  - secretName: {{ coalesce .Values.ingress.tls.secrets.hello .Values.ingress.tls.secrets.default }}
  {{- end }}
  rules:
  - host: {{ .Values.ingress.domain }}
    http:
      paths:
      - path: /hello-extension
        pathType: Prefix
        backend:
          service:
            name: hello-web-service
            port:
              number: 80
EOF
```