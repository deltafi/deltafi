# S3 Proxy Configuration Tuning

DeltaFi uses s3-proxy for an out-of-the-box local S3 compatible storage solution.

## Parameters

### Tunable Parameters

| Parameter | Default |  Description |
|-----------|------------|----------------|
| `requests.memory` | 256MB | Memory allocation per request. |
| `limits.memory` | 1GB | Maximum memory allocation for the container. |

## Overriding Parameters

### Kubernetes (Helm)

Override in your Helm values file:

```yaml
s3proxy:
  resources:
    requests:
      memory: 256Mi
    limits:
      memory: 1Gi
```

Values merge with defaults — you only need to specify what you're changing.

Then run `deltafi up` to apply.

## Sizing Recommendations

### requests.memory 

For processing larger sized Deltafiles on systems with greater than minimum specs, this should be configured upwards up to `1Gi`

General rule: This parameter should not need to exceed `1Gi`

### limits.memory

For high volume Deltafile processing on systems with greater than minimum specs, this should be configured upwards up to `64Gi`

General rule: This parameter can be increased in relation to available resources but should not need to exceed `64Gi`