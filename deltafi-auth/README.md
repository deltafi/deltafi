# DeltaFi Auth

This service provides certificate-based authentication/authorization to Kubernetes Ingress. This is done via [Kubernetes External Authentication](https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/annotations/#external-authentication).

The Kubernetes Deployment for this service must be configured with 2 environment variables:

 - __SECRET__ - The name of a Kubernetes Secret containing authorization information
 - __NAMESPACE__ - The Kubernetes namespace where the Secret resides

The secret must be of type `Opaque` and should include an `allowed` field where the value is a base64-encoded YAML map.

Here in an example of a secret:

```
apiVersion: v1
kind: Secret
type: Opaque
metadata:
  name: auth-secret
  namespace: deltafi
data:
  allowed: YWxsOgogIC0gIkNOPUhhbiBTb2xvLE9VPURldk9wcyxPVT1Db21wYW55IgpraWJhbmE6CiAgLSAiQ049TGFuZG8gQ2Fscmlzc2lhbixPVT1NYW5hZ21lbnQsT1U9Q29tcGFueSIKbmlmaToKICAtICJDTj1MdWtlIFNreXdhbGtlcixPVT1EYXRhZmxvdyxPVT1Db21wYW55Ig==
```

The YAML map keys should be subdomains (e.g. kibana, nifi, etc) with values that are arrays of certificate Distinguished Names (DN) that should have access to those subdomains.

A key of `all` grants access to all subdomains in the system.

For example:

```
all:
  - "CN=Han Solo,OU=DevOps,OU=Company"
kibana:
  - "CN=Lando Calrissian,OU=Managment,OU=Company"
nifi:
  - "CN=Luke Skywalker,OU=Dataflow,OU=Company"
```

### Testing

The run tests:

 ```
 bundle exec rspec --format doc
 ```
