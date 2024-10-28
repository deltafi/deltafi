# Install Plugins

DeltaFi plugins provide actions and flows for the system. For information on creating plugins, see the [Plugins](/plugins) section.

In this guide we will be installing the Passthrough plugin as an example. Other plugins can be installed using the `deltafi install-plugin` command.

## Install Command
Install the plugin using the `deltafi` command.

```
deltafi install-plugin "plugin-image"
```

If it succeeds, the output should look something like this:

```
>> Successfully installed plugin "plugin-image"
```

The `deltafi install-plugin` command supports the following flag to set an image pull secret
specific to the plugin image that is installed.
```
--pull-secret              Pull secret to use instead of using the pluginImagePullSecret system property
```

> **_NOTE:_**  The image pull secret can be set globally for all plugins using the `pluginImagePullSecret` system property.

## SSL Config

To provide a private key, certificate and a Certificate Authority (CA) chain to plugins run the `deltafi configure-plugin-ssl` command (see `deltafi configure-plugin-ssl --help` for more details).
When a plugin is deployed or restarted after this command, the plugin will have a populated `/certs` directory.
The command will also add the optional environment variables of `SSL_PROTOCOL` and `KEY_PASSWORD` when those fields are provided.
The `/certs` directory will contain the following files:
- `/certs/tls.key` (private key)
- `/certs/tls.crt` (certificate)
- `/certs/ca.crt` (CA chain)