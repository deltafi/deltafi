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