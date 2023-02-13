# Authentication

Authentication in DeltaFi is handled at the Kubernetes Ingress level. Each request that comes in is checked against the `deltafi-auth` service. This service can be configured for Basic Authentication (default) or Client Certificate Authentication. The system can only run in one mode at a time.

## Basic Authentication

By default, DeltaFi is configured for Basic Authentication. The default user is `admin` and the password can be set by running the following CLI command:

```
 deltafi set-admin-password
```

Additional users can be added on the Users page in the UI.

## Client Certificate Authentication

When configuring a system for Client Certificate Authentication, it is recommended to start with Basic Authentication, set up your users, and then switch to Client Certificate Authentication.

### Adding a User with a Client Certificate

Before switching to Client Certificate Authentication, be sure to add a new user with a Distinguished Name (DN).

1. Go to the __Users__ page in the UI.
1. Click on the __Add User__ button in the top right.
1. Enter the __Name__ of the new user.
1. In the __Authentication__ section, click on the __Certificate__ tab.
  - You will see a warning stating that the authentication mode is currently set to `basic` and that this must be set to `cert` before changes will take effect. This will be done in the next section.
1. Enter the __Distinguished Name (DN)__ of the user's client certificate.
1. Assign the user one or more __Roles__ (described below).
1. Click the __Save__ button.

> __Note:__ You can also add a __Distinguished Name (DN)__ to an existing user. It will be ignored until Client Certificate Authentication is enabled.

### Enabling Client Certificate Authentication

To enable Client Certificate Authentication (and disable Basic Authentication), complete the following steps:

1. Identify and obtain (in PEM format) the certificate chain for the Certificate Authorities that issued the client certificates you want to accept. Store this chain in a file named `ca-chain.crt`.
1. Create a Kubernetes secret in the `deltafi` namespace called `auth-secret` that includes the certificate chain from the previous step.
```bash
kubectl create secret generic auth-secret --from-file=ca.crt=ca-chain.crt
```
1. Set `deltafi.auth.mode` to `cert` in your `site.values.yaml` file (as described in the [Install DeltaFi Core](/install/core#install-core) section.)
```yaml
    deltafi:
      auth:
        mode: cert
```
1. Update DeltaFi with the configuration from the previous step.
```bash
deltafi update --values ~/site.values.yaml
```

Visiting the UI should no longer ask for a password and instead, look for a client certificate that satisfies both of the following criteria:

1. Is signed by one of the Certificate Authorities in the `auth-secret` secret.
1. Has a __Distinguished Name (DN)__ that matches that of a User in the system.

## Role-Based Access Control

Authorization in DeltaFi is built on a Role-Based Access Control (RBAC) model. Everything in DeltaFi is restricted to specific Permissions, Permissions are assigned to Roles, and Roles are assigned to Users. Users cannot be assigned Permissions directly - they must be assigned through one or more Roles.

```asciiflow
                               ┌────────────┐        ┌────────────┐
                    ┌──────────┤    Role    ├────────┤ Permission │
                    │          └────────────┘        └────────────┘
                    │
         ┌──────────┴─┐                              ┌────────────┐
         │    User    │                   ┌──────────┤ Permission │
         └──────────┬─┘                   │          └────────────┘
                    │                     │
                    │          ┌──────────┴─┐        ┌────────────┐
                    └──────────┤    Role    ├────────┤ Permission │
                               └──────────┬─┘        └────────────┘
                                          │
                                          │          ┌────────────┐
                                          └──────────┤ Permission │
                                                     └────────────┘
```

### Permissions

Permissions in DeltaFi are static and do not change between releases. A list of Permissions can be seen when creating Roles (see below).

> __Note:__ The `Admin` Permission is a catch-all Permission that will allow access to everything in the system. This should be used with caution.

### Roles

Roles include one or more Permissions and can be assigned to one or more Users.

#### Default Roles

DeltaFi includes three default Roles.

- `Admin` - This role includes the `Admin` Permission and allows access to everything in the system.
- `Ingress Only` - This role includes only the `DeltaFileIngress` Permission and should be assigned to non-person entity (NPE) accounts used only for sending data to the system.
- `Read Only` - This role includes only the Permissions needed to grant a "Read Only" experience to a User.

#### Creating Roles

1. Go to the __Roles__ page in the UI.
1. Click on the __Add Role__ button in the top right.
1. Enter the __Name__ of the new Role.
1. In the __Permissions__ section, select the Permissions you want to be assigned to the Role.
  - Hovering over a Permission name will give a brief description of the Permission.
1. Click the __Save__ button.

#### Assigning Roles to Users

1. Go to the __Users__ page in the UI.
1. Find the User you want to modify and click the Edit button (pencil icon) in the right-most column.
1. In the __Roles__ section, select the Role(s) you want to be assigned to the User.
1. Click the __Save__ button.

> __Note:__ User RBAC information is cached for one minute. Changes could take that long to go into effect.

## Entity Resolver

> __Note:__ The use of an Entity Resolver is an advanced topic and is not required for most DeltaFi instances.

DeltaFi supports the use of an Entity Resolver that allows Users to be identified by multiple entity identifiers (e.g. Distinguished Names). This method can be used to perform lookups in external authentication/authorization systems.

When an Entity Resolver is enabled in the DeltaFi system, on every request, the `deltafi-auth` service will call out to the Entity Resolver and provide the identifier for the User that made the initial request. The response should inform the `deltafi-auth` service of other entities by which the User should be identified. This response is cached, by default, for one minute.

### Building an Entity Resolver

An Entity Resolver is a simple application that can be written in any programming language as long as it can be packaged as a Docker image.

#### Interface

An Entity Resolver interfaces with DeltaFi through HTTP POST requests made from DeltaFi to the Entity Resolver. By default, all requests will:

- Be made on port `8080` to the `/` endpoint.
- Have a `Content-Type` of `application/json`.
- Include a body that is a JSON array containing one element - the primary identifier of the User.
  - When running in Basic Authentication mode, this will be the username.
  - When running in Client Certificate Authentication mode, this will be the Distinguished Name (DN).

DeltaFi expects a response that is also a JSON array that includes all the identifiers by which the User should be identified.

For example, a request from DeltaFi to an Entity Resolver might look like this:

```
POST / HTTP/1.1
Host: 127.0.0.1:8080
Content-Length: 12
Content-Type: application/json

["CN=Alice"]
```

An example response might be:

```
[
  "CN=Alice",
  "CN=Sales Managers"
]
```

In this example, the Entity Resolver is telling the DeltaFi that the User with the DN `CN=Alice` should _also_ be identified as a User with the DN `CN=Sales Managers`. This would allow a User to be created with the DN set to `CN=Sales Managers`. Any Roles granted to the __Sales Managers__ User would be granted to __Alice__ when she accessed the system.

#### Docker

When packaging an Entity Resolver in a Docker image, be sure to expose port `8080` in your Dockerfile.

```
EXPOSE 8080
```

### Enabling an Entity Resolver

Once you have an Entity Resolver Docker image built, you can configure DeltaFi to use it by modifying the `deltafi.auth.entityResolver` section in your `site.values.yaml` file (as described in the [Install DeltaFi Core](/install/core#install-core) section.)

```yaml
deltafi:
  auth:
    mode: cert
    entityResolver:
      enabled: true
      image: your-entity-resolver-image:1.0.0
```

Then update DeltaFi with the new configuration.

```
deltafi update --values ~/site.values.yaml
```
