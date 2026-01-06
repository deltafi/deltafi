# Entity Authorization

Entity Authorization in DeltaFi is handled on a per-request basis when a user makes a request to view content. When this feature is enabled, prior to retrieving the content the metadata associated with the content is sent to the configured entity authorizer service to determine if the user is authorized to view the content.

## Entity Authorizer Service

> __Note:__ The use of an Entity Authorizer service is an advanced topic and is not required for most DeltaFi instances.

DeltaFi supports the use of a pluggable Entity Authorizer. The Entity Authorizer is a service that is responsible for determining whether an entity should be granted access to the content based on the metadata associated with the content.

### Building an Entity Authorizer Service

An Entity Authorizer is a simple application that can be written in any programming language as long as it can be packaged as a Docker image.

#### Interface

An Entity Authorizer application interfaces with DeltaFi through HTTP POST requests made from DeltaFi to the Entity Authorizer service. By default, all requests will:

- Be made on port `8081` to the `/` endpoint.
- Have a `Content-Type` of `application/json`.
- Include a body that is a JSON object containing two elements - the primary identifier of the User, and a JSON object containing metadata.
  - When running in Basic Authentication mode, the identifier will be the username.
  - When running in Client Certificate Authentication mode, the identifier will be the Distinguished Name (DN).

DeltaFi expects a response that is also a JSON object that includes one required element, `authorized`, which is a boolean value and an optional element, `reason`, which can be populated to communicate why the entity was not authorized.

For example, a request from DeltaFi to an Entity Authorizer might look like this:

```
POST / HTTP/1.1
Host: 127.0.0.1:8081
Content-Length: 12
Content-Type: application/json

{
  "identifier": "CN=Alice",
  "metadata": {
    "role": "sales",
    "level": "two"
  }
}
```

An example response might be:

```
{
  "authorized": false
  "reason": "Alice is not authorized for level two data"
}
```

In this example, the Entity Authorization service is telling the DeltaFi that the User with the DN `CN=Alice` is _not authorized_ to view content with the given metadata. This will forbid `Alice` from viewing the content. If the response had `authorized: true`, Alice would be allowed to view the content.

#### Docker

When packaging an Entity Authorizer application in a Docker image, be sure to expose port `8081` in your Dockerfile. Alternatively, you can configure the URL for service in the `site/values.yaml` if it must use a different port.

```
EXPOSE 8081
```

### Enabling an Entity Authorizer

Once you have an Entity Authorizer Docker image built, you can configure DeltaFi to use it by modifying the `deltafi.auth.entityAuthorizer` section in your `site/values.yaml` file (as described in the [Install DeltaFi Core](/install/core#install-core) section.)

```yaml
deltafi:
  auth:
    entityAuthorizer:
      enabled: true
      image: your-entity-authorizer-image:1.0.0
      ssl:
        secret: entity-auth-keys
```

Then update DeltaFi with the new configuration.

```
deltafi up
```

#### Adding SSL Certificates to an Entity Authorizer

You can configure DeltaFi to inject key and certificate files into the Entity Authorizer container. The name of the secret containing the key/certificat files is controlled by the `deltafi.auth.entityAuthorizer.ssl.secret` setting in the `site/values.yaml`. By default, it uses the secret name of `ssl-secret`. To load the key and cert run the command below (note --secret-name is optional, by default it loads ssl-secrets).
```bash
deltafi ssl ca-chain load -c <ca-chain.crt>
deltafi ssl load --key <private.key> --cert <public.crt> --secret-name <name-from-values-yaml>
```

After running the setup and restarting the Entity Authorizer container, the application will have access to the following files:

- /certs/tls.key: The private key
- /certs/tls.crt: The certificate
- /certs/ca.crt: The CA certificate chain

#### Adding Config Files to an Entity Authorizer

To add configuration files to an Entity Authorizer add a `config` section under `entityAuthorizer` in the `site/values.yaml` file. This section should include a map of filenames to their corresponding file contents. The mapped files will be placed in the `/config` directory inside the Entity Authorizer container where they can be read by the application.

For example, if the Entity Authorizer requires a configuration file called `auth.yaml`, your `site/values.yaml` would look like this:

```yaml
deltafi:
  auth:
    entityAuthorizer:
      enabled: true
      image: your-entity-authorizer-image:1.0.0
      config:
        auth.yaml:
          authKey: level
          userAccess:
            Alice:
              - one
              - two
            Bob:
              - one
```

The configuration file would be available at `/config/auth.yaml`.
