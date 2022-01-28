# deltafi-config-server project

### Description
The deltafi-config-server is based on the Spring Boot Cloud Config Server. It provides a central location for all
DeltaFi components and plugins to get their configuration properties. It houses a list of all available properties
with a description of what they are used for. The list of core properties is loaded based on the property-metadata.json
found in the `src\main\resources`. Plugins will provide their own list of properties.

By default, the config-server will look for properties to serve to clients in the `./config` directory where the jar is running.

### Property Sources

From the highest precedence to lowest:

1. MongoDB
2. External (file system/git)
3. PropertySet default values (i.e. from property-metadata.json or plugin provided PropertySet)

### Deployment

#### File System Backend (Native)

This is the default deployment option. By default, the server will look for property files in the `./config` directory
based on the path of the running jar.

To change the default directory from `./config` use this environment variable:

- NATIVE_SEARCH_LOCATIONS: /path/with/properties 

#### Git Backend

To use a git repo instead of native file system backend set the following environment variable:
  - SPRING_PROFILES_ACTIVE: git

The config server can bet setup to read a repository with https, ssh or from a file path. When using https
you must provide a username and password. For ssh or file based repo you must provide a ssh key.

##### Over https
To use git over https use the following environment variables

- GIT_CONFIG_REPO - URL of the repo to clone (i.e. https://gitlab.com/some/repo.git)
- USERNAME - username associated with the token to authenticate with (defaults to config-server)
- CONFIG_TOKEN - token/password for the given USERNAME

##### Over SSH
This option sets the config-server up to authenticate over SSH. The following environment variables are used in this setup: 

- GIT_CONFIG_REPO - URl of the repo to clone (i.e. git@gitlab.com/some/repo.git or file:///path/to/repo)
- PRIVATE_KEY - private key to use for authentication (must start with -----BEGIN RSA PRIVATE KEY-----)
- IGNORE_LOCAL_SSH_SETTINGS - if false the server will look for keys in ~/.ssh/ and ignore the PRIVATE_KEY (defaults true)
- STRICT_HOST_KEY_CHECKING - set to false to disable strict checks (defaults to false)

#### Additional Optional Git Related Environment Variables
  - name: DEFAULT_LABEL # (commit id, branch name, or tag)
    value: main 
  - name: CLONE_ON_START
    value: "true" 

### Sample API Usage

See the `.\src\main\resources\schema\schema\properties-schema.graphql` for the full GraphQl Schema

#### Get All Properties

POST http://deltafi-config-server/graphql  
Content-Type: application/json  
```json
{
  "query": "query {getPropertySets {id displayName description properties {key value hidden editable refreshable}}}",
  "variables": { }
}
 ```

#### Update Properties

POST http://deltafi-config-server/graphql  
Content-Type: application/json
```json
{
  "query": "mutation($updates: [PropertyUpdate]!) {updateProperties(updates: $updates)}",
  "variables": {
    "updates": [
      {
        "propertySetId": "deltafi-common",
        "key": "deltafi.delete.onCompletion",
        "value": "true"
      },
      {
        "propertySetId": "deltafi-common",
        "key": "deltafi.delete.frequency",
        "value": "PT3M"
      },
      {
        "propertySetId": "action-kit",
        "key": "actions.action-polling-period-ms",
        "value": "100"
      }
    ]
  }
}
```

#### Remove Property Overrides

POST http://deltafi-config-server/graphql  
Content-Type: application/json
```json
{
  "query": "mutation($propertyIds: [PropertyId]!) {removePropertyOverrides(propertyIds: $propertyIds)}",
  "variables": {
    "propertyIds": [
      {
        "propertySetId": "deltafi-common",
        "key": "deltafi.delete.onCompletion"
      }
    ]
  }
}
```

#### Add PropertySet (add properties for a Plugin)

POST http://deltafi-config-server/graphql  
Content-Type: application/json  
```json
{
  "query": "mutation($propertySet: PropertySetInput!) {addPluginPropertySet(propertySet: $propertySet)}",
  "variables": {
    "propertySet": {
      "id": "stix-actions",
      "displayName": "Stix Actions Properties",
      "description": "Properties specific to the stix-actions plugin",
      "properties": [
        {
          "key": "stix-conversion-server.url",
          "value": "http://localhost:8000",
          "defaultValue": "http://localhost:8000",
          "description": "URL of the stix conversion server",
          "hidden": false,
          "editable": false,
          "refreshable": false
        }
      ]
    }
  }
}
```

#### Remove a PropertySet (remove a Plugin)

POST http://127.0.0.1:8888/graphql  
Content-Type: application/json  
```json
{
"query": "mutation {removePluginPropertySet(propertySetId: \"stix-actions\")}",
"variables": {}
}
```