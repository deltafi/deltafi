# Changes on branch `compose-nginx-ssl-config`
Document any changes on this branch here.
### Added
- Added the option to configure the ssl_protocols and ssl_ciphers in the `site/values.yaml` used in NGINX when running in compose, for example
```yaml
ingress:
  domain: your.deltafi.domain
  tls:
    enabled: true
    ssl_protocols: TLSv1.3
    ssl_ciphers: ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384
```

### Changed
- Changed the compose default NGINX ssl_ciphers to `ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384`

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
