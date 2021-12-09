# deltafi-config-server project

### Description
The deltafi-config-server is based on the Spring Boot Cloud Config Server. It provides a central location for all
DeltaFi components and plugins to get their configuration properties. It houses a list of all available properties
and what they are used for. The property values are stored in git or the filesystem, any local overrides will be stored in mongo.
The list of available properties is loaded based on the property-metadata.json found in the `src\main\resources`.

By default, the config-server will look for configuration files in the configured git repository. To disable git and use a local directory set
the following environment variables:
- SPRING_PROFILES_ACTIVE: native
- NATIVE_SEARCH_LOCATIONS: <path to search>

### Deployment

#### Git Backend

##### Over https
This is the default deployment setup. The following environment variables are used in this setup:

- GIT_CONFIG_REPO - URL of the repo to clone (i.e. https://gitlab.com/some/repo.git)
- USERNAME - username associated with the token to authenticate with (defaults to config-server)
- CONFIG_TOKEN - token/password for the given USERNAME

##### Over SSH
This option sets the config-server up to authenticate over SSH. The following environment variables are used in this setup: 

- GIT_CONFIG_REPO - URl of the repo to clone (i.e. git@gitlab.com/some/repo.git)
- PRIVATE_KEY - private key to use for authentication (must start with -----BEGIN RSA PRIVATE KEY-----)
- IGNORE_LOCAL_SSH_SETTINGS - if false the server will look for keys in ~/.ssh/ and ignore the PRIVATE_KEY (defaults true)
- STRICT_HOST_KEY_CHECKING - set to false to disable strict checks (defaults to false)

##### From a file
This option sets the config-server up to read from a directory where a git repo is already cloned

- GIT_CONFIG_REPO - Path to the cloned repo (i.e. file:///path/to/repo)

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