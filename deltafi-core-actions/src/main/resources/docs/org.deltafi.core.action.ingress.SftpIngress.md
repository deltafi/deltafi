# SftpIngress
Polls an SFTP server for files to ingress.

## Parameters
| Name      | Description                                                                    | Allowed Values | Required | Default |
|-----------|--------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| directory | The directory to poll                                                          | string         | ✔        |         |
| fileRegex | A regular expression that files must match to be ingressed                     | string         | ✔        |         |
| host      | The SFTP server host name                                                      | string         | ✔        |         |
| password  | The password. If not set, will use the private key from a configured keystore. | string         |          |         |
| port      | The SFTP server port                                                           | integer        | ✔        |         |
| username  | The user name                                                                  | string         | ✔        |         |

