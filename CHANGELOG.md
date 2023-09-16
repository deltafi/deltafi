# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

All [Unreleased] changes can be viewed in GitLab.

## [1.1.2] - 2023-09-16

### Fixed
- Clickhouse chart did not use default api image tag
- Fixed clickhouse initialization issue
- Fixed clickhouse dashboard issue

## [1.1.1] - 2023-09-15

### Added
- Added a `system-plugin` where flows and variables can be added and removed without a full plugin install
- Added a mutation, `removePluginVariables`, to remove variables from the system-plugin
- Include max errors and expected annotations in snapshots
- Added an options parameter to FeignClientFactory:build to allow connect and read timeouts to be set
- Configurable time-to-live duration for ETL Deltafiles table
- Added documentation for unit testing java actions
- Added javadocs to the `deltafi-action-kit-test` classes

### Changed
- Limit flow plan mutations to the system plugin. Attempting to add or remove flow plans to a plugin other than the system plugin will result in an error
- Change the `savePluginVariables` mutation to take a list of variables that is always added to the `system-plugin`
- Total Bytes on Dashboard now uses `totalBytes` instead of `referencedBytes`.
- Clickhouse ETL table renamed `deltafiles`
- Clickhouse Grafana charts are not loaded if clickhouse is disabled
- Clickhouse ETL table is partitioned to days to improve short range query performance
- Renamed ingress flows to normalize flows
- Sort events in action queues by action create date, so that requeued actions are moved to the correct place in line.

### Fixed
- Fixed bug on the Search page causing Booleans to be parsed incorrectly on page refresh.
- Fixed bug causing Date Picker to not respect UTC mode.
- Fixed database seeding issue on initial startup of `deltafi-auth`.
- Clickhouse flow-subflow chart had misnamed bytes graph
- Remove the `ingressFlowPlan` and `ingressFlow` collections if they are recreated after they have already been migrated to `normalizeFlowPlan` and `normalizeFlow` collections.

### Deprecated
- Clickhouse ETL table `deltafile` is no longer used

### Tech-Debt/Refactor
- Move flow information into structured object in snapshots
- Refactored API route layout
- Removed usage of the deprecated base test classes from the `deltafi-core-action` tests
- Software license formatter updated
- Update python library dependency versions

### Upgrade and Migration
- Upgraded base images for core docker images
- Java plugins using the gradle plugin will be based on deltafi/deltafi-java-jre:17.0.8-0
- Prototype `deltafile` table should be delete from clickhouse if clickhouse was enabled prior to this release
- Subsystem upgrades:
  - Grafana: 10.1.1 (deltafi/grafana:10.1.1-0)
  - Promtail: 2.9.0 (grafana/promtail:2.9.0)
  - Loki: 2.9.0 (grafana/loki:2.9.0)
  - Graphite: 1.1.10-5 (graphiteapp/graphite-statsd:1.1.10-5)
  - Clickhouse: 23.8.2-debian-11-r0 (bitnami/clickhouse:23.8.2-debian-11.r0)

## [1.1.0] - 2023-09-05

### Added
- Added custom default time buttons to calendars 
- Added Flow column to Parent/Child DeltaFiles tables on Viewer 
- Add ConvertContentTransformAction that converts between JSON, XML, and CSV. 
- ExtractJsonMetadataTransformAction - Extract JSON keys based on JSONPath and write them as metadata
- ExtractXmlMetadataTransformAction - Extract XML keys based on XPath and write them as metadata
- Add MetadataToContentTransformAction 
- `deltafi mongo-migrate` now accepts `-f` flag to force migrations to run, as well as a list of migrations to run
- Add RouteByCriteriaTransformAction - reinject or pass DeltaFiles based on criteria defined using Spring Expression Language (SpEL) 
- Add XsltTransformAction for XML transformations using XSLT 

### Changed
- `deltafi mongo-migrate` batches all migrations in a single exec to speed up the migration execution process
- Allow TransformActions to reinject.

### Fixed
- Fixed bug with Download Metadata button when viewing individual actions.
- Disable pod security context for clickhouse to avoid pod security failure
- Use the variable datatype to determine the object type when resolving placeholders

### Tech-Debt/Refactor
- Remove vestigial content processing code in the API 
- Fix compiler warning in apply resume policy code

### Upgrade and Migration
- Upgraded both `deltafi-api` and `deltafi-auth` to Ruby 3.2.2 (`deltafi-ruby-base:1.1.0`)

## [1.0.7] - 2023-08-22

### Added
- Added Cypress testing to Errors pages 
- Added Download metadata button to metadata viewer 
- Added basic cypress tests for all UI pages.
- Added action result assertions that can be used to check action results directly
- Added a helper class, `DeltaFiTestRunner`, for setting up unit tests with an in-memory ContentStorageService instance with methods to simplify loading and reading content.
- Added FORMAT Action to python action test kit
- Clickhouse instance in the kubernetes cluster
- Clickhouse ETL to sync deltafiles from MongoDB into Clickhouse
- Prototype Clickhouse dashboards
- CLI: Added clickhouse-cli command
- Install: Automatic creation of clickhouse secrets
- DetectMediaTypeTransformAction that uses Apache Tika to attempt to detect and assign mediaTypes. 
- Trigger netlify job to publish docs to docs.deltafi.org on public main branch commits
- Add FilterByCriteriaTransformAction - filter or pass DeltaFiles based on criteria defined using Spring Expression Language (SpEL) 
- Add ModifyMediaTypeTransformAction 
- Add ModifyMetadataTransformAction to core actions 

### Changed
- The `pkg_name` parameter for python test kit moved into ActionTest constructor, and renamed `test_name` parameter to `data_dir`
- Make masked parameters backward compatible without relying on the migration
- Resume with modified metadata now modifies metadata received by the retried action, not the original source metadata. If replacing an existing metadata field, the ".original" key is no longer created.
- Run each migration script file from a single exec command to avoid false positives when checking if a migration has previously run

### Fixed
- Fixed a bug on the Errors page related to the resumption of multiple groups of DeltaFiles.
- Action tests compare event objects instead of events converted to String. This allows unordered metadata in tests and ensures we won't have key collisions and unintended regex normalization in event Strings. 
- FormatManyResult in python action kit missing child `did`
- FormatResult in python action kit missing `deleteMetadataKeys`
- Action test framework was returning false positive passes.
- Enforce ordering in action tests
- Clean up handling of expected and actual content sizes being different in action tests
- LineSplitter correctly handles header-only content
- count distinct dids in errorSummaryByFlow and errorSummaryByMessage groups and do not list dids twice 

### Tech-Debt/Refactor
- In action tests, remove behavior of loading default content consisting of the filename if the specified test resource cacannot be loaded from disk.
- Provide action tests a way to supply content not loaded from resources on disk. 
- Action test framework still allowed metadata on content, this was no longer a part of the Action/DeltaFile API. 
- Separated flow from action name
- Cleanup warnings in the repository code

### Upgrade and Migration
- API, Monitor, and ETL Ruby gems updated to latest maintenance releases
- deltafi/grafana image updated to 10.0.3-0

## [1.0.6] - 2023-08-09

### Added
- Added filename search options to make regex and case-sensitive searches optional. For example: 
  ```graphql
  query {
    deltaFiles(
        filter: {sourceInfo: {filenameFilter: {filename: "stix.*", regex: true, caseSensitive: false}}}
    ) {
      deltaFiles {
        did
        sourceInfo {
          filename
        }
      }
    }
  }
  ```
- Test kit for DeltaFi Python TRANSFORM and LOAD actions
- Added -r flag to `deltafi registry add` command to replace previous tags with new one
- Added `api/v1/registry/repository/delete` endpoint to delete all tags in a repository
- Added `api/v1/registry/replace` endpoint to replace all tags in a repository with new tag
- Added the option to mark plugin variables as masked. Only users with `Admin` or `PluginVariableUpdate` roles will be able to see the masked parameter value 

### Changed
- gitlab-ci now only builds using packages and their versions in package.json
- FlowfileEgressAction record the HTTP response body in the error context now, instead of the error cause
- Internal docker registry is configured to load plugins via localhost routing.  Any plugin image loaded into the registry will be accessable for plugin install via `localhost:31333/<IMAGE NAME>`

### Fixed
- Fixed a UI issue when querying for errors with regex characters in the `errorCause` field.
- Fixed the logic used to determine if the FormatAction is complete when getting the content to send to the next action
- Fixed Errors Page not showing errors on all tab 
- Fix eslint broken pipeline by hard coding versions used "npm install eslint@8.40.0 eslint-plugin-vue@9.11.1"
- When a DeltaFile in an ERROR state has been acknowledged, clear the DeltaFile from the auto-resume schedule
- Clicking on an error message on the __By Message__ tab on the Errors page now filters on the associated flow.
- Throw exception and do not trigger disk space delete policies if the API reports 0 byte disk usage or total size
- Stop deleting batches in the disk space delete policy if the target disk space threshold has been reached
- "Failed writing body" error in CLI when events are generated
- Storage API now ignores invalid metrics and raises an exception if no valid metrics can be found for the time period.

### Removed
- Ingress removed for internal docker registry

### Tech-Debt/Refactor
- Updated CLI documentation
- Made Input classes consistent with Result and Event classes
- Renamed base Event classes
- Made LoadMany and FormatMany more consistent
- Test example for `FormatActionTest` from Java action test kit
- Cleaned up API rubocop warnings

### Upgrade and Migration
- Update to minio version RELEASE.2023-07-21T21-12-44Z 

## [1.0.5] - 2023-07-17

### Added
- Hide k8s dashboard in compose mode 
- New registry APIs added:
  - `api/v1/registry/add/path/to/registry:tag` - allows direct pull of any publicly accessable docker image into the local registry
```
curl -X POST http://local.deltafi.org/api/v1/registry/add/deltafi/deltafi-stix:1.0.0
```
  - `api/v1/registry/delete/path/to/registry:tag` - allows deletion of local registry image
```
curl -X DELETE http://local.deltafi.org/api/v1/registry/delete/deltafi/deltafi-stix:1.0.0
```
  - `api/v1/registry/list` - generates a json summary of all local registry repositories and their tags
- Added new indices to improve the performance of the `deltaFiles` query

### Changed
- Better handling of when DeltaFile content can't be found.

### Fixed
- Add the base64 flag check to the compose script 
- Docker registry image will automatically initialize the docker file system on initialization
  to avoid garbage collection errors with an empty registry

## [1.0.4] - 2023-07-09

### Added
- Added the ability to CRUD expected annotations on transform and egress flows from the GUI 
- `api/v1/registry/catalog` endpoint to list repositories in the internal registry
```
curl http://local.deltafi.org/api/v1/registry/catalog
```
- `api/v1/registry/upload` to upload a docker-archive tar file to the internal registry
```bash
# example
curl -X POST --data-binary @passthrough.tar http://local.deltafi.org/api/v1/registry/upload -H "name: deltafi/passthrough:1.0" -H "Content-Type: application/octet-stream"
```
- Three new permissions added to auth:
  - RegistryView
  - RegistryUpload
  - RegistryDelete
- KinD: Registry enabled by default
- KinD: Registry UI (http://registry.local.deltafi.org) enabled by default
- Added pending Read Receipts indicators to the deltafile viewer. 
- System Overview dashboard has a CPU utilization graph
- System Overview dashboard has a RAM utilization graph
- `cluster loc build` takes `noui` directive to skip UI build

### Changed
- Enabled registry deletion in internal registry by default
- Updated the pydantic allowed versions to keep it below version 2

### Fixed
- Fixed Deltafile Viewer blocking resume / replay for Deltafiles with deleted content 
- Add schema version to deltaFiles query to prevent spurious upconversions
- Fix possible NPEs in schema upconversion

### Tech-Debt/Refactor
- Move domains and enrichments from the top level of the DeltaFile to the actions that added them
- Fixed some gradle warnings due to obscured dependencies

### Upgrade and Migration
- Update deltafi spring base container image to 1.0.2
- Update to Palantir docker plugin 0.35.0
- Grafana upgrade to 10.0.1
- Upgrade kind image to 1.24.15 (to support KinD 0.20.0)
- Update python package dependencies to match those used in deltafi-python-base image:1.0.2 (python:3.10.12-slim)
- MongoDB upgrade to 5.0.17
- Redis upgrade to 7.0.11
- Promtail upgrade to 2.8.2
- Loki upgrade to 2.8.2
- Docker registry upgrade to 2.8.2

## [1.0.2] - 2023-06-29

### Added
- Added a query to get the set of annotations that are expected on a DeltaFile but not present 
- Added the `pendingAnnotationsForFlows` field to the `DeltaFile` graphql schema
- New mutation `applyResumePolicies` allows recently added auto resume policies to be retroactively applied to any oustanding DeltaFiles in the ERROR stage (whicn are still resumable)
- New user role `ResumePolicyApply` in the `Resume Policies` group grants permisson to execute the `applyResumePolicies` mutation

### Changed
- Clarified documentration that the `flow` in an auto resume policy refers to the DeltaFile's sourceInfo flow. I.e., the ingress or transformation flow name

### Fixed
- Nodemonitor used RAM calculation fixed

### Tech-Debt/Refactor
- Update the DeltaFiles in a new thread when expected annotations are changed to prevent blocking the graphql response 

## [1.0.1] - 2023-06-26

### Added
- Added a `DeltaFileFilter` to search for `DeltaFiles` that are waiting for annotations
- Added the ability to search for DeltaFiles with annotations that are pending from Search Page.

### Changed
- `ingressFlowErrorsExceeded` DGS query includes INGRESS and TRANSFORM flow details

### Fixed
- Max errors checked for ingress flows during REINJECT
- Max errors checked for transform flows during INGRESS and REINJECT

### Tech-Debt/Refactor
- remove unused federation.graphql file

## [1.0.0] - 2023-06-21

### Fixed
- Querying for the distinct annotation keys no longer fails if a DeltaFile is missing the `annotationKeys` field

### Deprecated
- All releases prior to 1.0.0 should now be considered deprecated and
  no longer supported.  All bug fixes and features will be only added
  to the 1.0.0 tree.

### Upgrade and Migration
- For upgrades to 1.0.0, the `deltafi-passthrough` plugin flows should be disabled
  and the plugin should be uninstalled.  The plugin is now built-in and the external
  plugin will cause conflicts.  This is not a concern for new installations

## [1.0.0-RC8] - 2023-06-19

### Added
- Include the resume policy name when retrieving snapshots in the CLI and GUI
- Registry chart has an enabled flag

### Changed
- Registry is disabled by default

### Fixed
- Fixed css removing all icons from buttons
- Return a mutable list when migrating enrichment to enrichments
- Fix the UnsupportedOperationException when max errors are set on both ingress and transform flows
- UI: CSS text alignment fix for buttons without icons
- CLI: Fixed bug in install command resulting in `command not found` error message

## [1.0.0-RC6] - 2023-06-15

### Added
- UI now displays deleteMetadataKeys in metadata viewer for each Action
- UI: Added loading indicator to "Acknowledge All" button in Notifications panel

### Changed
- The `NoEgressFlowConfiguredAction` error can now be auto resumed
- Created default auto resume policies for no egress flow configured and storage read errors

### Fixed
- Fixed css issues with new datepicker component used in search page and events page
- Fixed file upload and metadata buttons css issues on delta file upload page
- Fixed warnings in JS console on Errors Page being thrown by acknowledged expecting Boolean got Undefined bug
- Bug with base64 on Linux in kind/cluster (-w flag default differs between OSes)
- Fix issue with generics that always assigned the first generic type found to ActionParameters class causing Jackson serialization issues
- KinD: Add subdirectories needed for registry cleanup jobs

### Tech-Debt/Refactor
- Make license headers not javadoc
- Refactor Result and Event classes
- Standardize builder method name to "builder"

### Upgrade and Migration
- Update docker base images:
  - deltafi-python-base:1.0.0
  - deltafi-spring-base:1.0.0
  - deltafi-ruby-base:1.0.0
  - nginx:1.25.1-alpine
  - alpine:3.18.2

## [1.0.0-RC5] - 2023-06-12

### Added
- Updated the date/component on the Search Page
- Updated calendar component on Events Page
- Added a `deltafi disable` command that disables all DeltaFi processes
- Added a `deltafi reenable` command that reenables all DeltaFi processes
- Added a `deltafi scale` command that allows you to edit the replica counts across all deployments and statefulsets and then apply the changes
- Descriptions and "Hello World" examples to action documentation
- Increased validation checks for ActionEvent
- Added the option to have python plugins pass their coordinates into the plugin init method
- Added the option to have python plugins specify the package where actions can be found and loaded
- Added Makefile support to the cluster command for building plugin images
- Added a new `cluster expose` command that exposes the services necessary to run a plugin outside the cluster
- Added a new `cluster plugin run` command that will run the plugin outside the cluster

### Changed
- Renamed the `compose stop` command to `compose uninstall`
- Renamed the `compose stop-service` command to `compose stop-services`

### Fixed
- The core now performs an extra check for every requeued DeltaFile to ensure it is not already in the
  queue. The redis ZSET already checks for exact matches, but in cases where a different returnAddress
  is used there were still opportunities for duplication

### Removed
- Removed unused `time` field from ActionEvent

### Tech-Debt/Refactor
- Refactored the Search Page
- Removed items from the deltafi-cli/config.template that should not be configurable
- Renamed enrichment to enrichments
- JSON object serialization/deserialization for Redis data moved to ActionEventQueue
- Additional tests for handling invalid ActionEvents
- DeltaFile: Merge formatted data content and metadata into actions
- Handle multiple format actions in the DeltaFile actions array. This is prep work to allow for more flexible resume scenarios

### Upgrade and Migration
- The python plugin method has changed, the description is now the first argument followed by optional arguments
  ```python
  # Before
   Plugin([
    HelloWorldDomainAction,
    HelloWorldEgressAction,
    HelloWorldEnrichAction,
    HelloWorldFormatAction,
    HelloWorldLoadAction,
    HelloWorldLoadManyAction,
    HelloWorldTransformAction,
    HelloWorldValidateAction],
    "Proof of concept for Python plugins").run()
  # After (using action_package instead specifying the action list
  Plugin("Proof of concept for Python plugins",  action_package="deltafi_python_poc.actions").run() 
  ```

## [1.0.0-RC3] - 2023-06-02

### Added
- `deltafi-docker-registry` pod added to cluster
- `local.plugin.registry` will resolve to the local plugin registry in repository configuration for plugins
- Added a new field, `expectedAnnotations`, in the transform and egress flows containing a set of annotations that are expected when a `DeltaFile` goes through the flow
- Added new mutations to set the expected annotations in transform and egress flows
   ```graphql
   # Example mutation setting the expected annotations on a transform flow named passthrough
   mutation {
     setTransformFlowExpectedAnnotations(flowName:"passthrough", expectedAnnotations:["readBy", "readAt"])
   }
   # Example mutation setting the expected annotations on an egress flow named passthrough
   mutation {
     setEgressFlowExpectedAnnotations(flowName:"passthrough", expectedAnnotations:["readBy", "readAt"])
   }
   ```
- Serialized Errors Page State
- Transform and Load actions can delete metadata
- Allow Tranform and Load Actions to create annotations (formerly known as indexed metadata)
- Add stricter validation for events received by core from actions

### Changed
- View All Metadata dialog now uses new top-level metadata key to display cumulative metadata
- System Overview dashboard limits list of ingress and egress flow totals to 10 items each
- Delete policies will not remove a `DeltaFile` until all expected annotations have been set
- Updated UI libraries
- Use `errorCause` and `errorContext` to detail an invalid action event, and verify in test
- Loki retention rules limit retention of noisy cruft logs
- Ensure mongo migrations are only run once
- UI now prefetches pages. This reduces load times when switches pages
- Rename indexedMetadata to annotations and indexedMetadataKeys to annotationKeys
- Updated FeignClientFactory to support URIs passed to interface methods

### Fixed
- Replay toast message now displays the new DID of the replayed DeltaFile
- Updated text color of the DID column of a selected row on the Search page
- Allow unselecting of rows on the Search page
- Fixed bug in Domain and Enrichment viewers
- `cluster` did not recognize "17" as a valid Java 17.x version

### Tech-Debt/Refactor
- DeltaFile: Merge protocol stack content, metadata, and deletedMetadataKeys into actions
- Fix tests that would occasionally fail because of non-deterministic sorting of equal OffsetDateTimes

### Upgrade and Migration
- Added new custom grafana image deltafi/grafana:9.5.2-2

## [1.0.0-RC2] - 2023-05-18

### Fixed
- Provide a more detailed error message when the deleteRunner fails
- Disallow old wire format format result events with missing content

## [1.0.0-RC1] - 2023-05-17

### Added
- Added a `terminalStage` filter to the `DeltaFilesFilter`. When `terminalStage` is true it will find DeltaFiles that are in a terminal stage, when false it will find DeltaFiles that are in an in-flight stage
- Added the ability to search for DeltaFiles in a terminal stage from Search Page
- Add Toast message when plugin upgrade/install request is made
- Added visual indicator to Search Page when filter are applied
- System Properties Page now shows editable Plugin variables by Plugin
- CHANGELOG added to DeltaFi documentation
- Java Action Kit: add save interfaces for String, in addition to existing byte[] and InputStream interfaces

### Changed
- `StorageCheck` now respects `check.contentStoragePercentThreshold` system property
- Database migrations in auth are now completed before workers are spawned
- Auth `WORKERS` are now set to `8` by default
- Plugin init uses the action input to build the flow plans with classes that are generated
- The plugin action templates now includes boilerplate code to read and write content
- Annotate Icon was changed to tag
- Java Action Kit exceptions publish with the correct cause, instead of burying the message in the context
- Do not include the actionType field when generating plugin flows
- Test execution extraneous logging is silenced

### Fixed
- Fixed bug on Search page when applying and clearing filters
- Dialogs that contain forms no longer have a dismissible mask
- Fixed bug causing `ContentStorageCheck` to never report
- Fixed issue preventing auth `WORKERS` being set to greater than one
- Add the MINIO_PARTSIZE environment variable to plugins deployed in standalone mode
- Correctly assign processingType on reinject
- Alerts that are silenced will no longer generate a notification

### Tech-Debt/Refactor
- Flatten content object by removing content references and adding segments and mediaType directly to content
- Introduce DeltaFile schema versioning for backward compatibility
- Remove unneccessary ProtocolLayer from load and transform response wire protocols
- Remove sourceInfo from the wire protocol
- Update versions of Python package dependencies

### Documentation
- Update 'Getting Started' tutoral to reflect recent changes

## [0.109.0] - 2023-05-11

### Added
- Added External Links page to UI which allows a user to CRUD External Links and DeltaFile Links
- Added Next Auto Resume to DeltaFile Viewer and Errors pages
- Added the ability to Annotate DeltaFiles
- Added ProcessingType to DeltaFile View and search
- A new mutation `replaceDeltaFileLink` that is used to replace an existing DeltaFile link
    ```graphql
    # Example usage
    mutation {
      replaceDeltaFileLink(
        linkName: "oldName"
        link: {name: "newName", url: "new.url", description: "new description"}
      )
    }
    ```
- Added a new mutation `replaceExternalLink` that is used to replace an existing external link
    ```graphql
    # Example usage
    mutation {
      replaceExternalLink(
        linkName: "oldName"
        link: {name: "newName", url: "new.url", description: "new description"}
      )
    }
    ```
- Passthrough plugin merged into the core and no longer an independent plugin

### Changed
- Parent/Child DeltaFile queries are now batched on the DeltaFile viewer
- DIDs are now normalized (to lowercase) on DeltaFile Viewer page
- Modified the Layout of the Errors Page
  - Removed the `Next Auto Resume` column
  - Added an indicator icon for `Next Auto Resume` to the `Last Error` column
  - Truncated the Filename column
  - Enhanced the column widths
- The interfaces for loading and saving Content in the Java Action Kit have been reworked
To retrieve content as a byte array, string, or from a stream:
```java
byte[] byteArray = content.loadBytes();
String string = content.loadString();
String encodedString = content.loadString(Charset.forName(encoding));
InputStream inputStream = content.loadInputStream();
```
To store and add content and add to a Result:
```java
// create a result of the type appropriate for your Action
TransformResult transformResult = new TransformResult(context);
transformResult.saveContent(byteArray, fileName, MediaType.APPLICATION_JSON);
transformResult.saveContent(inputStream, fileName, MediaType.APPLICATION_JSON);
// you can reuse existing content and add it to the Result without having to save new Content to disk:
List<ActionContent> existingContentList = input.getContentList();
transformResult.addContent(existingContentList);
// you can also manipulate existing content
ActionContent copyOfFirstContent = existingContentList.get(0).copy();
copyOfFirstContent.setName("new-name.txt");
// get the first 50 bytes
ActionContent partOfSecondContent = existingContentList.get(1).subcontent(0, 50);
copyOfFirstContent.append(partOfSecondContent);
// store the pointers to the stitched-together content without writing to disk
transformResult.addContent(copyOfFirstContent);
```
- The interfaces for loading and saving Content in the Python Action Kit have been reworked.
To retrieve content as bytes or a string:
```python
bytes = content.load_bytes()
string = content.load_string()
```
To store and add content and add to a Result:
```python
// create a result of the type appropriate for your Action
result = TransformResult(context)
result.save_byte_content(bytes_data, filename, 'application/json')
result.save_string_content(string_data, filename, 'application/xml')
// you can reuse existing content and add it to the Result without having to save new Content to disk:
existing_content = input.first_content()
result.add_content(existing_content)
// you can also manipulate existing content
copy = input.first_content().copy()
copy.set_name("new-name.txt")
// get the first 50 bytes of the next piece of incoming content
sub_content = input.content[1].subcontent(0, 50)
copy.append(sub_content)
// store the pointers to the stitched-together content without writing to disk
result.add_content(sub_content)
```
- The compose environment files are now generated based on yaml configuration that is passed into the start command

### Fixed
- Update requeue and resume logic to look for actions defined in transform flows
- Fixed bug related to DeltaFiles with many children on the DeltaFile viewer
- Fixed issues with unexpected or missing metrics
- Fixed a bug that caused the system to report incorrect RAM usage on systems with large amounts of RAM (>100G)
- Fixed the logic for determining which flows and flow plans need to be removed on a plugin upgrade
- New plugin information is not written unless all parts of the plugin registration are valid
- Preserve the maxError settings when ingress flows and transform flows are rebuilt
- Add all content to result when saving many
- Fixed wrapping issue in UI menus
- Fixed overscroll behavior in UI
- Fix regression where IngressAction always showed 0 ms duration
- Fixed bug with mediaType not being populated when viewing certain content on the DeltaFile Viewer page
- Fix a null pointer exception that could occur when formatting K8S events generated for a plugin pod
- Fix a null pointer exception that could occur when the MinioClient returns a null ObjectWriteResponse
- The compose command no longer depends on relative paths
- Provide default values for runningTransformFlows and testTransformFlows in snapshots for backward compatibility

### Removed
- JoinAction was completely removed.  Will be reintroduced with a revamped design in future release
- Remove ingressFlow from ActionInput interfaces, since it is available in the ActionContext

### Tech-Debt/Refactor
- Make DeltaFile metadata accumulate as it travels through Transform and Load Actions.  Transform and Load Actions receive the original metadata plus any metadata that has been added by other actions that proceed it.  Metadata produced by a Format Action is still received by Validate and Egress Actions as it was sent, not including the metadata of any other actions that proceeded it
- Remove sourceMetadata from ActionInput interfaces
- Updated python action kit with new wire protocol interfaces
- Rename SplitResult to ReinjectResult to better capture semantics.  SPLIT action state is now REINJECTED
- Move sourceFilename from the action inputs to the action context, since it is common to all actions

### Upgrade and Migration
- Upgraded DGS Codgen to 5.7.1
- Upgraded DGS to 6.0.5
- Upgraded Spring Boot to 3.0.6
- Upgraded Jackson to 2.15.0
- Upgraded Jackson Schema Generator to 4.31.1
- Upgraded JUnit Jupiter 5.9.3
- Upgraded Mockito JUnit Jupiter 5.3.1
- Upgrade spring docker image to deltafi-spring-base:1.0-1

## [0.108.0] - 2023-04-21

### Added
- Added Auto Resume to UI
- Added priority to auto resume queries. Added editable priority to auto resume table and added priority to auto resume configuration dialog
- Support the following commands when running with docker-compose
   - install
   - uninstall
   - mongo-migrate
   - minio-cli
   - secrets
- Added a stop-service function to the compose script that can be used to stop individual containers
- Support for ingress of V3 and V2 NiFi FlowFiles
- DeltaFi now supports two processing modes:
  - NORMALIZATION - the classic processing mode, consisting of Ingress, Enrich, and Egress flows
  - TRANSFORMATION - a new mode consisting of linear Transform flows. A transform flow has a series of TransformActions followed by an EgressAction.  If the final TransformAction in the chain produces multiple pieces of content, they will all be egressed using child DeltaFiles.  For example:
```json
{
  "name": "simple-transform",
  "type": "TRANSFORM",
  "description": "A simple transform flow that processes data and sends it out using REST",
  "transformActions": [
    {
      "name": "FirstTransformAction",
      "type": "org.deltafi.example.action.FirstTransformAction"
    },
    {
      "name": "SecondTransformAction",
      "type": "org.deltafi.example.action.SecondTransformAction"
    }
  ],
  "egressAction": {
    "name": "SimpleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "egressFlow": "simpleTransformEgressFlow",
      "metadataKey": "deltafiMetadata",
      "url": "${egressUrl}"
    }
  }
}
```

### Changed
- Use the `api/v1/versions` endpoint to get the running versions instead of relying on k8s
- Do not allow resume policies using the `NoEgressFlowConfiguredAction` action name
- When specifying the `action` field for an auto resume policy, it must include the flow name prefix

### Fixed
- Added a static namespace label to fix the `Log Overview` dashboard when running in compose
- Added the labels required for log scraping to the plugin containers
- Secret env variables are no longer quoted when they are generated to support the `minio-cli` command
- Fixed the check used to determine if there are mongo collections to drop on uninstall
- The `deltafi-api` now always attempts to retrieve properties from `deltafi-core`
- Java action kit: allow flow-only plugins with no actions

### Removed
- Remove Content metadata field

### Tech-Debt/Refactor
- ObjectStorageExceptions no longer need to be caught in Java actions when loading or storing data
- Extract CoreApplicationTest helper methods and constants into separate units
- Added a UUIDGenerator that can be replaced with TestUUIDGenerator for tests
- Moved remaining business logic from IngressRest to IngressService
- Hide FormattedData from ValidateInput and EgressInput.  Add input methods:
  - loadFormattedDataStream
  - loadFormattedDataBytes
  - getFilename
  - getFormattedDataSize
  - getMediaType
  - getMetadata
- Add content loading methods to TransformInput, loadInput, EnrichInput, and FormatInput:
  - contentListSize
  - loadContentBytes
  - loadContentBytes(index)
  - loadContentStream
  - loadContentStream(index)
- Renamed MetricRepository to MetricService
- Move content storage methods from the Action classes to the Result classes, combining store and append result into one step
For example, instead of:
```java
ContentReference reference = saveContent(did, decompressed, MediaType.APPLICATION_OCTET_STREAM);
Content content = new Content(contentName, reference);
result.addContent(content);
```
Now:
```java
result.saveContent(decompressed, contentName, MediaType.APPLICATION_OCTET_STREAM)
```
- Add join tests to main core application test suite to speed up tests and remove dependency on java-action-kit
- Resolve numerous compiler warnings
- Modify saveMany data storage interface to take an ordered map of names to byte arrays

## [0.107.0] - 2023-04-19

### Added
- New `priority` field in `ResumePolicy`, which is automatically computed if not set
- Generate an event and snapshot prior to running an upgrade
- Docker compose mode introduced as a beta proof of concept
  - From the `compose` directory execute `./compose start`
  - To use the CLI you must unlink the cluster command (execute `deltafi-cli/install.sh`) and add
    `export DELTAFI_MODE=STANDALONE` to `deltafi-cli/config`
- `appsByNode` endpoint added to core to get docker-based apps-by-node manifest
- `app/version` endpoint added to core to get docker-base version list
- `DockerDeployerService` added to manage plugin installs in compose
- `DockerAppInfoService` added to support `appsByNode` and `app/version` endpoints

### Changed
- Resume policy search is now in `priority` order (the highest value first)
- The resume policy name stored in the DeltaFile `nextAutoResumeReason` is no longer cleared when the DeltaFile is resumed
- Monitored status checks for k8s will only run in CLUSTER mode
- CLI updated to accommodate standalone compose mode

### Fixed
- Resume policy search will consider the number of action attempts and the policy `maxAttempts` during the policy search iteration loop, not after finding the first match
- sse endpoint X-Accel-Buffering turned off to fix stand alone sse streaming

### Tech-Debt/Refactor
- Clean up public methods in Java Action Kit Input and Result interfaces

### Upgrade and Migration
- Added `priority` to `resumePolicy` collection

## [0.106.0] - 2023-04-17

### Added
- Added the ability to bulk replay DeltaFiles
- Added DeltaFiles Search filter for Replayable
- Added a `replayble` filter that returns a list of `DeltaFiles` that can be replayed when set to true
- New metric `files_auto_resumed` per ingress flow for DeltaFiles auto-resumed
- Add monitoring for flow deactivation and reactivation due to the maxErrors threshold
- New required field `name` to auto-resume policies
- New field `nextAutoResumeReason` set to auto-resume policy name when policy is applied
- Added additional logging for Python plugin startup
- Added a `replayed` filter that returns a list of `DeltaFiles` that have been replayed when set to true

### Changed
- The nodemonitor to reports CPU and memory metrics to Graphite
- Changes to the API endpoint for nodes metrics (`/api/v1/metrics/system/nodes`):
  - Node metrics are now pulled from Graphite instead of Kubernetes
  - Pods are now referred to as Apps
  - App metrics are no longer reported
- The UI now shows App information on the System Metrics page instead Pod information. No per-app metrics are displayed
- Helm charts are now local charts rather than helm dependencies
- The minimum value for the Auto Resume Policy `maxAttempts` is now 2
- UI changes to support flow cache changes
- changelog tool will add a "latest.md" during release

### Fixed
- Python action kit allows `Domain.value` and `Content.name` to be optional for event execution
- Fixed bug where storage check was always getting 0% disk usage
- Fixed bug preventing system snapshot imports.
- Resume of a DeltaFile when the last error was `No Egress Flow Configured` resumes at the `ENRICH` stage
- Projection fields in the `deltaFiles` query no longer treats fields which start with the same name as another field as duplicates
- Rolled back SQLite gem to fix startup error in Auth
- Fixed the `export-ingress-plan`, `export-enrich-plan`, `export-egress-plan`, and `export-rules` commands
- Fixed truncate error in commands when running on macOS
- Fixed bug on DeltaFile Viewer page when there are actions in the protocolStack without any content
- The `deltaFileStats` query no longer throws a reflection error
- Plugin Docker images being tagged with an unspecified version
- Do not remove content when the next action fails to queue after ingress, allow requeue to process it

### Security
- Fixed Ruby base CVEs

### Tech-Debt/Refactor
- Reduce mongo queries for flow information
- Pull FlowFile unpacking out of IngressRest
- Move `DeltafiApiClient` failure tracking to the `DiskSpaceService`

### Upgrade and Migration
- Before upgrading, run `utils/helminator.sh` to clean up helm dependencies
- Upgraded helm charts for:
  - mongodb
  - grafana
  - promtail
  - graphite
- Upgraded Grafana to 9.4.7
- Upgraded Promtail to 2.8.0
- Upgraded Loki to 2.8.0
- Upgraded Graphite to 1.1.10-4
- Upgraded Redis to 7.0.8
- KinD: Upgraded metrics-server to v0.6.3
- Added `name` field to `resumePolicy` collection
- Added `name` field to `resumePolicies` in `systemSnapshot` collection
- Added `nextAutoResumeReason` field to `deltaFile` collection where `nextAutoResume` is set
- Updated all base images to 1.0
- Upgrade to Spring Boot 3.0.5
- Upgrade to Lombok 1.18.26
- Upgrade to DGS 6.0.1
- Upgrade to DGS Codegen 5.7.0
- Upgrade to Jackson 2.14.2
- Updated gems for all Ruby projects

## [0.104.4] - 2023-04-03

### Fixed
- Issue where replicated plugins could lead to lost variable values on registration
- Uploading metadata without an ingress flow on the Upload Page no longer clears the dropdown
- Uploading metadata with an invalid ingress flow will result in a warning and the flow being ignored
- Bug requiring an image pull secret on the plugin repository form

## [0.104.3] - 2023-03-30

### Added
- Added an editable Max Errors column to Flows page
- Added task timing to Gradle scripts

### Changed
- Flow descriptions are now truncated on flows page to make cell sizes all the same. A tooltip popup shows the full description on flows with truncated descriptions when hovered over. Added the full description to flow viewer
- Refactored CI pipelines to remove Docker in Docker
- In deltafi-monitor read the api URL from an environment variable
- In deltafi-egress-sink read the ingress and core URLs from an environment variable

### Fixed
- Bug requiring all users to have at least one metrics related permission
- Tooltip on multiple pages causing mouse flicker
- Do not move to the `Egress` stage until all pending `Enrich` stage actions are complete
- `changelog` does not generate a bad error message when there are no unreleased changelog files
- Do not run the UI property migration if the UI properties already exist in the system properties

### Tech-Debt/Refactor
- Using Kaniko for UI docker build

### Documentation
- Added code of conduct at `CODE_OF_CONDUCT.md`

## [0.104.0] - 2023-03-23

### Added
- Added the `LoadManyResult` to the python action-kit
- `changelog` script added to manage and deconflict changelog entries
- Experimental DeltaFile cache feature. By default, this is turned off with the deltaFileCache.enabled feature flag
Flip to true to test it. To see if it is working, try processing some files while watching `deltafi redis-watch | grep BZPOPMIN`
When enabled you should see delivery to many different topics, one for each core and worker that is running
With it off, all messages will be delivered to the dgs topic. When on, DeltaFiles will be cached locally and made eventually
consistent in the database. This decreases processing latency but does not give a realtime view of the state in the UI
- DeltaFile metrics for total/in flight files and bytes
- DeltaFile metrics graphs added to system summary dashboard
- Added `/blackhole` endpoint to `deltafi-egress-sink`.  The endpoint will always return a 200 and never write content to disk, acting as a noop egress destination.  The endpoint will take a latency parameter to add latency to the post response (i.e. `/blackhole?latency=0.1` to add 100ms latency)
- MergeContentJoinAction that merges content by binary concatenation, TAR, ZIP, AR, TAR.GZ, or TAR.XZ
- Added ingress, survey, and error documentation
- Resume policies are examined when an action ERROR occurs to see if the action can be automatically scheduled for resume
- New Resume Policy permissions
  - `ResumePolicyCreate` - allows user to create an auto-resume policy
  - `ResumePolicyRead` - allows user to view auto-resume policies
  - `ResumePolicyUpdate` - allows user to edit an auto-resume policy
  - `ResumePolicyDelete` - allows user to remove an auto-resume policy
- New `autoResumeCheckFrequency` system property to control how often the auto-resume task runs
- Added `nextAutoResume` timestamp to DeltaFile
- Added auto-resume documentation
- New properties for plugin deployments
  - `plugins.deployTimeout` - controls how long to wait for a plugin to successfully deploy
  - `plugins.autoRollback` - when true, rollback deployments that do not succeed prior to the deployTimeout elapsing
- Added `maxErrors` property to ingress flows. By default this is set to 0. If set to a number greater than 0,
ingress for a flow with at least that many unacknowledged errors will be blocked. This is based on a cached value,
so ingress cutoffs will not be exact, meaning more errors than what has been configured can accumulate before ingress
is stopped
- Added support for configuring the number of threads per Java action type via properties. To specify the thread count
for an action type, include a section like the following in your application.yaml file:
```yaml
actions:
  actionThreads:
    org.deltafi.core.action.FilterEgressAction: 2
```
- Join action support to the Python DeltaFi Action Kit

### Changed
- Survey metrics filter out zero series from tables and charts
- Updated the load-plans command to take plugin coordinates as an argument
- Updated system snapshots to include new `autoResumeCheckFrequency` property and auto-resume policies
- Plugin installations will wait until the deployment has rolled out successfully
- Failed plugin installations will now return related events and logs in the list of errors
- Create the child dids in the action-kit for LoadManyResults, so they can be used in the load action
- Changed location for plugin running file to /tmp directory to allow running outside of docker for testing purposes
- Join actions can now return ErrorResult and FilterResult

### Fixed
- CLI: Line wrapping fixed in `list-plans` command
- Improved bounce and plugin restart performance in `cluster`
- A metric bug with illegal characters in tags
- DeltaFiles in the JOINING stage are now considered "in-flight" when getting DeltaFile stats
- Replaced deprecated GitLab CI variables
- Fixed unknown enum startup errors when rolling back DeltaFi
- Fix memory leak in ingress when using deltaFileCache
- Fix deltaFileCache race condition where querying from the UI could cause the cache to repopulate from the database and lose state

### Removed
- Removed `nextExecution` timestamp from Action; no migration required since it had not been used previously
- Removed blackhole pod, which was superseded by the egress-sink blackhole endpoint

### Tech-Debt/Refactor
- More precise calculation of referencedBytes and totalBytes - remove assumption that segments are contiguous
- Perform batched delete updates in a bulk operation
- Ingress routing rules cache did not clear when restoring a snapshot with no rules
- Fix the check to determine if a plugin-uninstall was successful
- Services that were created for a plugin are removed when the plugin is uninstalled
- Refactored StateMachine

### Upgrade and Migration
- With the change to the plugin running location, all plugins will need to be rebuilt using this DeltaFi core so that
the application and plugins can run successfully

## [0.103.0] - 2023-03-09

### Highlights
- New annotation endpoint supports external annotation of indexed metadata
- Grafana has been integrated with DeltaFi RBAC
- CLI and `cluster` command have been streamlined and improved
- Minio upgrades and performance optimizations
- Full support for Kubernetes 1.24

### Added
- Python Duplicate Log Entry cleared up
- New fields for DeltaFile, Action, and new RetryPolicy data structures to support forthcoming automatic retry configuration
- New permission `DeltaFileMetadataWrite` that allows a user to update indexed metadata on a `DeltaFile`
- Annotation endpoints that add indexedMetadata to a `DeltaFile`:
  - `/deltafile/annotate/{did}?k=v&kn=vn` - if a key already exists the value will not be replaced
  - `/deltafile/annotate/{did}/allowOverwrites?k=v&kn=vn` - if a key already exists the value will be changed
- `cluster loc destroy` can be used to destroy a cluster when you are doing local KinD cluster operations
- CLI: `deltafi query -c` option to colorize output
- New Metrics-related permissions
  - `MetricsAdmin` - Grants the `Admin` role in Grafana
  - `MetricsEdit` - Grants the `Editor` role in Grafana
- CacheAutoConfiguration to enable caches to be configured in application properties when caffeine is present

### Changed
- KinD: `deltafi ingress` works with regular filesystem paths instead of paths relative to the project root
- KinD: `cluster` command streamlined output for readability
- CLI: `deltafi query` does not colorize results as default behavior
- Metrics: Grafana auth is now tied to DeltaFi auth

### Removed
- CLI: Command logging removed.  Not very useful and caused error codes to be hidden

### Fixed
- Survey endpoint parameter counts renamed files for consistency
- `deltafi install-plugin` did not set error codes on failure
- `deltafi uninstall-plugin` did not set error codes on failure
- `deltafi serviceip` did not set error codes on failure
- `deltafi did` and `deltafi list-*` commands were not working under some circumstances in KinD cluster
- Bug where durations could not be properly converted when importing a `SystemSnapshot`
- Egress sync smoke survey updated with new API changes
- Diagnostic dashboard latency chart was not displaying properly
- Cluster command generated warnings on admin.conf mode
- Cluster uninstall timeouts
- KinD: several `deltafi` CLI commands no longer proxy through to the cluster container, improving performance
- `cluster` command syncs the cluster unnecessarily
- Bug resulting in Grafana Alert Check being denied access to Grafana API
- Possible stream resource leak in egress actions
- CLI: commands for ingress-flow and enrich-flow were broken
- Fixed issue with generating a metric on an egress URL with encoded parameters

### Tech-Debt/Refactor
- Files in minio are now stored in subfolders by the first 3 characters of the did

### Upgrade and Migration
- Minio chart 5.0.7 and minio RELEASE.2023-02-10T18-48-39Z
  > Helm chart upgrade is required to migrate.  `helm dependency upgrade`
- KinD kubernetes node updated to 1.24.7
- KinD metrics server upgrade to 6.2
- All plugins must be rebuilt against this release
- Pre-upgraded content in minio will be unreachable until it is migrated. To prevent any disruption to running flows, follow these upgrade procedures:
1. stop ingress by changing the system property `ingress.enabled` to `false`
1. wait for all in-flight system in the data to complete processing
1. perform the system update
1. run `utils/subfolderize.sh PATH_TO_MINIO_STORAGE` on the minio node,
where the PATH_TO_MINIO_STORAGE is the location of your storage bucket, e.g. /data/deltafi/minio/storage.  Wait patiently for the script to complete
1. turn ingress back on by changing the system property `ingress.enabled` to `true`

## [0.102.0] - 2022-02-24

### Added
- Add deltaFileStats GraphQL query
- `Survey Metrics` dashboard in Grafana
- Disk space and Delete activity added to UI charts
- Set environment variable `DELTAFI_PYTHON` in cluster loc that allows downstream python projects to pick up the local python action kit
- Server Sent Events broadcast for deltaFileStats
- Delete metrics (files and bytes by policy) accumulated and charted on System Status dashboard
- ContentSplitter that splits content into multiple sub-references pointing to segments of the original content
- Ingress flows now accept either a load or a join action. A join action will execute when a configurable number of
files are received or a configurable max age is reached. The join action receives the combined DeltaFile and a list of its
joined DeltaFiles

### Changed
- Survey endpoint added support for survey subflows and direction
- Survey metric retention lowered in Graphite
- Survey metrics changed to:
  - `stats_count.survey.files`
  - `stats_count.survey.bytes`
  - `stats_count.survey.subflow.files`
  - `stats_count.survey.subflow.bytes`
- Refactored Monitor to prevent checks from blocking one another

### Fixed
- Executing `deltafi` commands in KinD now writes files as the current user instead of root:root
- Ingress was writing to disk when a flow was disabled or non-existent
- No longer storing unneeded `stats.*` metrics in Graphite database.  This is a 50% reduction in metric storage
- Run the property migration if any snapshots exist where DeltaFiProperties is not set
- Values issue in KinD local values
- Check if a plugin contains a flows directory before trying to use it
- `FormatMany` children are no longer incorrectly marked as being in `testMode`

### Tech-Debt/Refactor
- Clean up indexes that store nested data
- Adjust requeue query to hit completed before index

### Upgrade and Migration
- Updated docker base images to 0.102.0

## [0.101.5] - 2022-02-10

### Added
- Additional KinD caching registry for registry.k8s.io
- Added `cluster plugin` command to KinD CLI for building and installing local plugins

### Changed
- Monitor now pulls System Properties from Core and caches them
- CLI: `version` command prints the DeltaFi core version

### Fixed
- Monitor will now reconnect to MongoDB if the connection is lost
- KinD metric-server architecture selected correctly on linux arm64 VMs
- Set auth workers back to 1

### Upgrade and Migration
- Update to latest Docker base images
- Remove `locked` field from `deletePolicy` collection, and delete policies in `systemSnapshot` collection

## [0.101.4] - 2022-02-09

### Added
- Add a top-level DeltaFile field listing indexedMetadata keys
- Add totalCount endpoint to give estimated total count of deltaFiles
- Added heartbeat to Server-Sent Events (SSE) connections

### Changed
- Send notification count via Server-Sent Events (SSE)
- Cache calls to k8s and graphite from the API's content endpoint
- Cache permissions in Auth
- Increased default worker threads in Auth and API
- Changed the MinIO delete cleanup interval from 5 minutes to 1 minute
- Improve domains endpoint performance
- Improve performance of indexedMetadataKeys endpoint
- Cap total count at 50k for deltaFiles query to improve mongodb performance
- Only set indicies and bucket age off if `schedule.maintenance` is not set to false
- Storage efficient un-tar for regular TAR files
- Use a standard output location when generating plugins
- Unzip generated plugins by default, add an option to zip them
- Add plugin image repository and customization settings to the snapshots

### Removed
- Locked option from delete policies
- Remove deleteOnCompletion option to prevent split/join problems
- Removed default delete policies

### Fixed
- Fixed problem with publishing all Gradle plugins to Gradle Central
- Add mongodb index to fix disk space delete policy query performance
- Check for ageOff property changes when properties are refreshed and update the TTLs if needed
- Allow delete batch size to be changed dynamically
- Fix issues with searching indexed metadata
- Fixed issue with plugin test harness not allowing reading the content more than once

### Upgrade and Migration
- Updated deltafi-build, deltafi-ruby-base, deltafi-spring-base and deltafi-kind-node base images to 0.101.4

## [0.101.3] - 2023-02-01

### Added
- Timestamp index to Events collection in Mongo
- Events generated when ingress is disabled due to content storage depletion
- A `startupProbe` in plugin deployments that waits for actions to listen for work

### Changed
- Improved event summary for plugin installation/uninstall
- Restrict `_id` field in Event creation/updating
- The scheduled execution time for `delete` and `requeue` jobs are calculated based on the last execution time instead of last completion time

### Deprecated

### Removed

### Fixed

### Tech-Debt/Refactor

### Security

### Upgrade and Migration

## [0.101.2] - 2023-01-30

### Added
- Memory profiler for Monitor when at DEBUG log level
- Time range query support to Event API
- Unacknowledge endpoint to Event API

### Changed
- Sort order for Event API is now by timestamp descending

### Fixed
- Fixed Mongo Connection thread issue in Monitor
- Formatting on migration event content
- CLI: Cleaned up event output on install command
- Monitor logger did not log errors with backtrace correctly

## [0.101.1] - 2023-01-26

### Added
- Java `EventService` for generating events to the event API from the core
- Events generated when a new core is installed from the CLI
- Events generated when a plugin is installed or uninstalled
- Added a new endpoint `generate/plugin` used to create new plugin projects
- CLI: `deltafi plugin-init` used to create new plugin projects

### Fixed
- Fix API call NPEs

## [0.101.0] - 2023-01-23

### Added
- Added Summary field to Events
- CLI: `deltafi event list` and `deltafi event create` commands added
- Grafana alerts create events when they are initiated and cleared

### Changed
- Upgrade all Java containers to build with JDK17 and execute with JVM17

### Removed
- Alerts from Grafana no longer trigger a failed status check.  Events will be used to track alerts

### Fixed
- Corrected Python license headers
- Fix caching of API storage check calls from ingress

### Upgrade and Migration
- Upgrade to MongoDB 5.0.14
- Upgrade to Redis 7.0.7
- Upgrade to Grafana 9.3.2
- Upgrade to Promtail 2.7.1
- Upgrade to Loki 2.7.1
- Upgrade to Graphite 1.1.10-4
- Upgrade to Spring Boot 3.0.1
- Upgrade to DGS 6.0.0
- Upgrade to DGS Codegen 5.6.5
- New base image: deltafi/deltafi-spring-base:jdk17
- Upgrade to Gradle 7.6.0

## [0.100.0] - 2023-01-14

### Added
- Add new referencedBytes field to the DeltaFile that sums the size of all data referenced by that DeltaFile, even if the data was stored as part of another DeltaFile
- Added Event API
- Add lightweight metrics-only survey endpoint (https://{base}/survey?flow={flow}&count={count}&bytes={bytes})

### Changed
- Auth now sends JSON to entity resolver
- Version reckoning Gradle plugin will accept x.y.z-nn as a valid tagged version
- Changed data structure for storing various metadata fields in the DeltaFile from a list of key values to a map
- Updated documentation for latest plugin structure, action interfaces, and Python action kit

### Removed
- GraphQL endpoints for action responses have been removed

### Fixed
- Version reckoning Gradle plugin will default to 0.0.0-SNAPSHOT in an untagged repository

### Upgrade and Migration
- Existing deltaFiles will have a referencedBytes field that is set to the value of totalBytes.  New DeltaFiles will have the referencedBytes field set correctly
- Upgraded docker base images:
  - deltafi/deltafi-ruby-base:0.100.0
  - deltafi/deltafi-spring-base:0.100.0
  - deltafi/deltafi-kind-node:0.100.0

## [0.99.8] - 2023-01-09

### Added
- Added `nocore` directive to `cluster loc build` command to shortcut and avoid building the core when developing plugins

### Removed
- CLI: `stop` command removed (was a confusing alias for uninstall)

### Fixed
- Regressed OpenFeign to v11, since v12 included slf4j-api 2.0, which is not compatible with springboot and caused logging to be disabled
- In the python action kit, when a plugin does not have a `flows` directory, log a warning instead of an unhandled exception
- CLI: Improved warning when uninstalling DeltaFi
- `cluster` command will warn if the `deltafi` command is not linked to the expected location
- Action `start_time` in Python action kit now recorded before action execution, not after

### Tech-Debt/Refactor
- Added unit testing for python modules

### Upgrade and Migration
- Update Spring Boot base image to deltafi/spring-boot-base:0.99.8
- Update Ruby base image to deltafi/deltafi-ruby-base:0.99.8

## [0.99.6] - 2022-12-18

### Added
- Alternate `bootstrap-dev.sh` script to bootstrap a dev environment
- Bootstrap installer supports Debian and Ubuntu
- New mutations added to add and remove DeltaFile links and external links:
  - `saveExternalLink`
  - `saveDeltaFileLink`
  - `removeExternalLink`
  - `removeDeltaFileLink`

### Changed
- `cluster prerequisites` will install python and attempt to install Fedora/Debian dependencies
- Restoring a `system-snapshot` now defaults to a hard reset
- System properties are stored as json instead of key value pairs
- Plugins are configured through environment variables
- Search for multiple `DeltaFilesFilter:egressFlows` values is now done as an OR operation instead of AND
- Use Ruby base image for API, Auth, and Egress Sink
- Moved the UI configuration from the `deltafi-ui-config` ConfigMap into the `DeltaFiProperties`

### Removed
- The `config-server` has been removed
- Mongo migrations for DeltaFi versions older than 0.99.4 have been removed

### Fixed
- Incorrect creation of metadata exceptions in python action kit

### Tech-Debt/Refactor
- Added unit testing for several python modules

### Security
- Added CVE patches for commons-text, snakeyaml, kubernetes-client
- New Ubuntu base image for Spring apps with clean CVE record

### Upgrade and Migration
- Java dependency updates:
  - DGS 5.5.0
  - DGS Codegen 5.6.3
  - Jackson 2.14.1
  - Jedis 4.3.1
  - Minio 8.4.6
  - Spring Boot 2.7.6
  - OpenFeign 12.1
  - nifi-flowfile-packager 1.19.1
  - dropwizard metrics-core 4.2.13
  - json-schema-validator 1.0.74
  - slf4j-api 1.7.36
  - maven-artifact 3.8.6
  - org.json json 20220924
- Java test dependency updates:
  - TestContainers 1.17.6
  - mockito-junit-jupiter 4.9.0
  - junit-jupiter-api 5.9.1
  - junit-jupiter-engine 5.9.1
- Docker base image updated to 0.99.6-1
- System properties collection changed from `propertySet` to `deltaFiProperties` with new structure, and reflected in `systemSnapshot`
- The UI configuration is moved from the `deltafi-ui-config` ConfigMap to `deltaFiProperties`, and added into the `systemSnapshots`

## [0.99.5] - 2022-12-08

### Added
- Audit entries for deleted dids
- Added new `ingressFlows` field to SourceInfo for DeltaFiles filter
- Allow custom metrics to be returned in responses from python actions
- All Java Action Kit result classes have a custom metric `add` method for adding custom metrics to a result
- `filteredCause` added to DeltaFile search filter for GraphQL queries

### Changed
- `SplitResult::splitInputs` renamed to `splitEvents` to match naming convention
- DeltaFi Gradle convention plugin ids have been shortened to `org.deltafi.version-reckoning`,
`org.deltafi.java-convention`, and `org.deltafi.test-summary`
- The DeltaFi Gradle action convention plugin id has changed to `org.deltafi.plugin-convention`
- Make the scheduled service thread pool size property editable
- Changed Python `EgressResult` to require `destination` and `bytes_egressed` in order to produce metrics
- Filtered DeltaFile cause is now recorded in the action `filteredCause` field instead of the `errorCause` field

### Deprecated
- Deprecated `flow` field in DeltaFiles SourceInfo filtering; use 'ingressFlows' instead.  `flow` will still work at the moment, but will be removed in a future release

### Fixed
- Issue where syncing properties was thrashing between multiple instances of `deltafi-core`
- Ingressing zero-byte file returned an error code, despite successful processing
- Remove flow plans and flows that are no longer part of a plugin on upgrade
- Setup SSL properties to bind to the `KEYSTORE_PASSWORD` and `TRUSTSTORE_PASSWORD` environment variables to remain backwards compatible
- Issue with local Minio forked chart not setting fsGroupChangePolicy

### Tech-Debt/Refactor
- Move MinIO bucket setup from common to core
- Use a single age off property under delete properties for both content and metadata
- Move metrics out of common into core to prevent Java based actions from connecting to `graphite`

### Upgrade and Migration
- MinIO helm chart updated to `8.0.10-deltafi-r1`
- Filtered DeltaFile cause migrated from `errorCause` field to `filteredCause` field

## [0.99.4] - 2022-11-22

### Added
- New query `getFlowNames` that returns a list of flow names grouped by flow type and can be filtered by flow state
- Grafana alerts will be displayed in the main UI monitor list and cause a degraded state
- Python action development kit
- Enhanced test framework for plugins

### Changed
- Add fsGroupChangePolicy: OnRootMismatch to (dramatically) speed up minio pod startup
- Metrics are now generated from the core for all actions.  Custom metrics are reported to the core via an addition to the ActionEventInput schema

### Removed
- `files_completed` metric was removed.  It was redundant with other metric information and not used in dashboards

### Fixed
- Issue in audit logger causing username to be tagged to logs erroneously
- Issue with delete action performance
- Issue with delete action and requeue scheduling
- Action metrics API updated to use `files_in` instead of `files_completed` metric

### Upgrade and Migration
- Upgrade base SpringBoot docker image to 0.99.4
- Upgrade Kind node docker image to 0.99.4
- Gradle upgraded to 7.5.1

## [0.99.3] - 2022-11-14

### Added
- Flow Summary dashboard allows filtering by egress flow
- Processing Report dashboard allows filtering by egress flow
- Create a snapshot prior to running a plugin deployment
- Core API version is now discoverable via query
- Delete snapshot mutation
- `GraphQLExecutor` and GraphQL client added to the common core library
- API: Flow metrics can be filtered by egress flow
```
example query:
https://deltafi.org/api/v1/metrics/flow?range=this-month&aggregate[My Composite]=decompress-passthrough,decompress-and-merge&egressFlows[]=passthrough
```

### Changed
- Increase default requeueSeconds from 30 to 300
- Create a snapshot prior to running a plugin deployment
- Core API version is now discoverable via query
- Consolidate Action input parameters: each action now takes a context, parameters, and a single action input (e.g. LoadInput, ValidateInput, etc.) that contains the other members specific to that action
- Flowfile-v1 metadata extraction now no longer uses the SAX parser--which doesn't like 4-byte encoded UTF-8--allowing more multi-lingual support

### Removed
- Remove simple- and multi- variants of Action interfaces

### Fixed
- Test mode will be preserved when plugins are registered
- `Plugin` upgrades no longer reset the replicas to 1
- Issue with publishing plugins to gradle.org
- Fix stressTest regression with 0 byte files
- Action queue API endpoint no longer shows inactive queues

### Upgrade and Migration
- Plugins will need to be upgraded to the new action base classes
- Updated core Java dependencies:
  - DGS version 5.4.3
  - Jackson version 2.14.0
  - Minio client 8.4.5
  - Spring Boot 2.7.5

## [0.99.2] - 2022-11-07

### Added
- `testFlow` is now a property of `FlowStatus`.  This is used to indicate if a flow is in test mode, meaning that it
is not intended to be egressed from the system
- New mutations added to enable and disable test flows:
  - `enableIngressTestFlow`
  - `disableIngressTestFlow`
  - `enableEgressTestFlow`
  - `disableEgressTestFlow`
- `DeltaFile`s now indicate if they were processed with a test flow by setting a `testMode` flag and populating a
`testFlowReason` field to indicate the reason for being processed in test mode
- `deltaFiles` search filter for `testMode`
- System snapshots will snapshot the `testMode` status for a flow
- System configuration `deltafi.ingress.enabled` to globally enable or disable all ingress
- System configuration `deltafi.ingress.diskSpaceRequirementInMb` to specify amount of disk space required to allow ingress
- Ingress backpressure will occur when the content storage does not have the required free storage space.  Ingress will
return a `507` error when there is not enough free content storage
- Single-step bootstrap installer script added for MacOS, CentOS, and Rocky Linux
- Allow DeltaFiles to be queried by filter message
- Added saveMany to the content storage service

### Changed
- ContentReferences are now composed of a list of on-disk segments

### Deprecated
- paramClass parameter in action registration is no longer needed

### Removed
- Remove invalid action types: MultipartEgressAction, SimpleMultipartEgressAction, MultipartValidateAction, SimpleMultipartValidateAction

### Fixed
- KinD: Fixed issue where `cluster loc clean build` had a race condition
- KinD: On MacOS arm64, the bitnami-shell installation was fixed
- Pods that complete (seen when running cron jobs) were causing a degraded state
- Properly close PushbackInputStream when saving content
- Ambiguous require ordering in deltafi-api service classes caused issues on Debian
- Fixed Minio issue with saving large numbers of files at a time in decompress stage by using saveMany/Minio snowball interface

### Tech-Debt/Refactor
- Groundwork laid out for RBAC permissions.  Permissions are currently hard coded to "admin"
- Removed invalid action types

### Upgrade and Migration
- Snapshot migration was added to add empty arrays for the new testFlow fields

## [0.99.1] - 2022-10-26

### Added
- New Grafana dashboard that shows last seen information for each flow
- Docs repo merged into DeltaFi monolith
- A checkDeltafiPlugin task that validates plugin variables and flow files has been added. It may be used in a
plugin's build.gradle by adding `id "org.deltafi.plugin" version "${deltafiVersion}"` to plugins and including
`bootJar.dependsOn(checkDeltafiPlugin)`
- New CLI commands: `list-plugins`, `system-property`, `plugin-customization`, `plugin-image-repo`
- DeployerService in `deltafi-core` that manages plugin deployments
- Common action deployment template in `deltafi-core` used by all plugins
- Convention Gradle plugins were added to the DeltaFi Gradle plugin:
  - `id 'org.deltafi.plugin.version-reckoning' version "${deltafiVersion}"` - add to a root-level project to set the
  project version from git
  - `id 'org.deltafi.plugin.test-summary' version "${deltafiVersion}"` - add to a root-level project to produce a
  summary of test results for the project and all submodules
  - `id "org.deltafi.plugin.java-convention" version "${deltafiVersion}"` - common Java conventions
  - `id "org.deltafi.plugin.action-convention" version "${deltafiVersion}"` - add to a project containing actions for a
  DeltaFi plugin

### Changed
- FlowPlan and ActionConfiguration classes now enforce required fields. They are final and must be provided to the
constructor
- CLI commands `install-plugin` and `uninstall-plugin` now take `groupId:artifactid:version` of the plugin
- The `uninstallPlugin` mutation no longer takes a dry-run flag
- Move delete policy "Preparing to execute" log messages to DEBUG level
- Make action name an optional field on transform and load completion, and have the core fill it in (don't trust the action to fill it)
- Disk delete policy only deletes files that have completed processing or have been cancelled

### Removed
- Plugin manifest generation has been removed from the DeltaFi Gradle plugin

### Fixed
- Flow summary dashboard ingress files query was incorrect
- State machine did not block waiting for all enrichment to complete
- KinD: deltafi-docs built during local build process
- Intermittent redis failures could cause DeltaFiles to be incorrectly placed into an ERROR state

### Tech-Debt/Refactor
- CLI: Clean up of output for migrations
- CLI: Migrations will only run if the core pod has been replaced
- KinD: Speed enhancements
- KinD: Clean up the cluster script and make shellcheck 100% happy

### Upgrade and Migration
- The `kind/cluster.yaml` plugins section requires `plugin_coordinates` per plugin
- Upgraded to mongodb 5.0.13

## [0.99.0] - 2022-10-12

### Added
- DeltaFile contains egress flow name for each egress flow executed
- Enable advanced alerting in Grafana
- Enable iframe embedding from Grafana
- Added ErrorByFiatTransformAction for erroring a flow
- Added FilterByFiatTransformAction for filtering a flow
- `values-alerting-test.yaml` added to preconfigure some alerting rules in KinD
- Added alert summary to System Overview dashboard
- Docs pod enabled by default
- UI dashboard added to Grafana
- Thread count for the DeltaFilesService is a configurable property
- Introduced a new `deltafi-core-worker` pod that can be scaled up to add capacity to the singleton `deltafi-core`

### Changed
- SourceInfo filename in deltaFiles query matches on case insensitive substring
- Optimize error detection when a split result does not match a running ingress flow
- Set explicit value for datasource UIDs in Grafana
- ActionDescriptor now contains the schema. All ActionSchema classes have been removed
- requiresEnrichment changed to requiresEnrichments
- Relaxed startup and readiness probe times
- Graphite probe timings are relaxed to avoid overzealous restarts
- Graphite helm chart is now a local chart
- New plugin structure ("Plugins v2")
- Zero byte files are no longer stored in Minio

### Deprecated
- The gradle-plugin is no longer needed to generate a plugin manifest. It is now generated on plugin startup

### Removed
- The ActionProcessor annotation processor is no longer needed to discover @Action-annotated classes and has\
been removed

### Fixed
- Fixed slow Monitor probe that was causing erroneous restarts
- System Overview dashboard uniformly uses SI measurements for bytes
- Errors on requeued DeltaFiles due to missing flows were not properly marked
- Removed hard coded datasource references from Grafana dashboards
- Pod status probe no longer reports "undefined method `any?' for nil:NilClass" when scaling deployments
- Monitor correctly parses GraphQL errors
- Ingress did not detect ingress routing or flow state changes unless restarted
- Add index for domain names
- Fix FILTER command from TransformActions
- Fix ERROR command from EnrichActions
- Bug in Graphite does not gracefully allow for null tags.  Removed Dropped Files metric from the report, since it may have null ingressFlow tags
- Improved initialization time for deltafi-core

### Tech-Debt/Refactor
- Do not store 0-byte files in minio
- Use Kubernetes node name in nodemonitor metrics
- Merge ingress code into core. Start ingress as a separate core instance with most core services disabled. Remove the overhead of an internal HTTP call on ingress
- Plugins now register themselves with their actions, variables, and flows on startup
- Unused Spring Boot services disabled by configuration in core
- Restrict Result types for each Action type

### Upgrade and Migration
- Upgrade Grafana to 9.1.7
- Upgrade Grafana helm chart to 6.40.3.  Air-gapped installs will need this new chart
- Base docker image updated to `deltafi/deltafi-spring-base:0.99.0`
- KinD: Node image updated to `deltafi/deltafi-kind-node:0.99.0`
- Graphite chart is now a local chart versioned as 0.99.0.  Air-gapped installs will need this new chart
- Upgraded to Redis 7.0.5.  Air-gapped installations will need the new Redis image
- Plugins now require expanded Spring boot info in build.gradle (plugin dependencies are optional):
```
springBoot {
    buildInfo {
        properties {
            additional = [
                    description: "Provides conversions to/from STIX 1.X and 2.1 formats",
                    actionKitVersion: "${deltafiVersion}"
                    pluginDependencies: "org.deltafi:deltafi-core-actions:${deltafiVersion},org.deltafi:deltafi-passthrough:1.0.0"
            ]
        }
    }
}
```
- Helm charts are (currently) still used by install-plugin/uninstall-plugin. They require a group annotation in\
Chart.yaml:
```
annotations:
  group: org.deltafi.passthrough
```
- Plugin flow files now require a type field. Valid values are INGRESS, ENRICH, and EGRESS
- Plugin variables.json files need to have the extra "variables" field removed, making it just an array of variables

## [0.98.5] - 2022-10-03

### Added
- New `cancel` mutation for DeltaFiles
- Added `requeueCount` to DeltaFile
- Added `rawDeltaFile` query to return all DeltaFile fields as JSON
- Added error when a split result does not match a running ingress flow

### Fixed
- Update the metric reports to use the `ingress` action tag
- Improved error handling in Content Storage Check

### Tech-Debt/Refactor
- Optimize batch resume, replay, and acknowledge operations
- Optimize empty content when running stressTest

## [0.98.4] - 2022-09-28

### Added
- Support `application/flowfile-v1` contentType in ingress

### Changed
- Replaced `resetFromSnapshot` with `importSnapshot`

## [0.98.3] - 2022-09-26

### Changed
- Enable SSL if the keystore password is set, remove the `ssl.enabled` property

### Fixed
- Return ErrorResult from uncaught Action exceptions
- Standardized Metrics tagging

### Tech-Debt/Refactor
- Redis connection pool scales to action count, and no longer used by ingress

## [0.98.2] - 2022-09-21

### Added
- Added an optional `reason` to the snapshots

### Fixed
- Disk space delete policy properly ignores deltaFiles if content is empty or already deleted

## [0.98.1] - 2022-09-21

### Added
- Added optional documentation server deployment enabled via values.yaml
- Made `totalBytes` field in DeltaFile searchable
- Added `deltafi.coreServiceThreads` property to configure core thread pool

### Changed
- Increased action queue check threshold
- Decreased action registration period
- Improved default delete policy names and configuration
- Egress does not generate smoke when artificial-enrichment is disabled
- Modified DID search to be case-insensitive
- Allow disk use on MongoDB aggregations

### Removed
- Removed deprecated type, produces, and consumes fields

### Fixed
- `files_errored` metric was not incremented when an error occurred due to no configured egress
- KinD bug with cluster.yaml diffs

## [0.98.0] - 2022-09-19

### Added
- ActionKit action log entries are tagged with the originating action name
- Action logs are labeled by promtail and indexed by Loki
- New action logs dashboard allows per action aggregated log viewing
- Added action log chart to action overview dashboard
- Statsd aggregation layer added to Graphite stack
- Custom Statsd reporter for delta metrics added to the common library and used in ingress and action-kit metrics
- Added API for flow metrics reports (`metrics/flow.json` and `metrics/flow.csv`)
- Added `DomainActions` that provide a global validation and metadata extraction path for domains
- Added content storage check to API system checks
- Added heartbeat to Monitor and configured the Kubernetes probe to use that for liveliness
- Added better Redis exception handling to API/Monitor
- KinD: Added Linux compatability to `cluster` command
- DecompressionTransformAction will log error results
- Add domains and indexedMetadataKeys graphQL endpoints
- Add metadata parameter to stressTest
- KinD: Configurable plugin list
- `minio.expiration-days` property is editable

### Changed
- Action-kit contains a logback.xml configuration file to configure logging for all actions
- Ingress converted to a Spring Boot app
- Metric reported to Graphite are now delta metrics, instead of monotonically increasing
- Flow metrics in Graphite begin with `stats_counts.` prefix
- Micrometer metrics dependency removed.  Metrics are now based on Dropwizard metrics
- All system Grafana dashboards are moved to a `DeltaFi` folder
- Metric dashboard charts have increased resolution to avoid inaccuracies introduced by linear regression
- Delete policy now has a name that is independent of the policy ID
- Consolidated all Spring Boot base docker images to a common 40% smaller, JRE-only image (`deltafi-spring-base`)
- Multiple Domain and Enrich Actions of the same time can now be configured as long as they do not operate on the same domains
- Renamed `RedisService` to `ActionEventQueue`, consolidated all take and put methods into it, and moved it to `deltafi-common` (property prefix remains "redis")
- Consolidated GraphQL client code to a new `org.deltafi.common.graphql.dgs` package in `deltafi-common`
- Moved the `HttpService` to a new `org.deltafi.common.http` package in `deltafi-common`
- Moved SSL configuration properties to the top level instead of being under actions
- Added auto configuration classes to deltafi-common and deltafi-actionkit. It's no longer necessary to specify base packages to scan
- Removed `@Configuration` from `@ConfigurationProperties` classes. The @ConfigurationPropertiesScan in the auto configurations doesn't need it
- Migrated Java StatsD client from UDP to TCP to guarantee metric delivery and handle metrics back-up when graphite is down
- Changed name of deltafi-core-domain to deltafi-core
- Disabled spring log banners in deltafi-ingress and deltafi-core-actions
- MinIO storage creation now done in deltafi-core instead of helm
- Flow assignment rules now has a name that is independent of the rule ID
- Removed "egressFlow" parameter for EgressActions
- Changed "minio.expiration-days" property to be editable and refreshable

### Deprecated
- Quarkus is no longer in use or supported in the DeltaFi monolith

### Removed
- Liveness probes removed from deltafi-core-actions, since it is no longer a web application and exposes no monitoring endpoints

### Fixed
- Resolution loss and dropped initial metrics issues are resolved in dashboards
- Bitrate gauges on dashboards no longer flatline periodically
- Metric summaries are now accurate at large time scales
- Metrics reported from multiple replicas will now be aggregated correctly
- Audit log dashboard will show all known users, instead of limiting to users active in the time window
- Application log dashboard will show all known apps, instead of limiting to apps active in the time window
- Bug in Action Queue Check initialization

### Tech-Debt/Refactor
- All dashboards updated for delta metrics

### Upgrade and Migration
- Legacy flow/action metrics will no longer appear in Grafana dashboards.  On update, only the new delta metrics will be displayed on dashboards
- Java dependencies updated, including:
  - DGS 5.2.1
  - DGS Codegen 5.3.1
  - Jackson 2.13.4
  - Log4J 2.18.0
  - Lombok 1.18.24
  - MinIO 8.4.3
  - Spring Boot 2.7.3
  - Spring Cloud 2021.0.3
- Upgraded all Spring Boot base images to `deltafi/deltafi-spring-base:0.97.0`
- Upgraded KinD node image to `deltafi/deltafi-kind-node:0.97.0` for KinD 0.15.0 compatibility
- `@EnableConfigurationProperties` needs to be replaced with `@ConfigurationPropertiesScan(basePackages = "org.deltafi")` in Spring Boot applications and tests
- Plugins pointing to deltafi-core-domain-service will need to change to new name at deltafi-core-service
- Plugin actions should remove their logback.xml configuration in order to pick up the common configuration

## [0.97.0] - 2022-08-29

### Added
- KinD: `cluster prerequisites` command will help get KinD prereqs installed on MacOS
- KinD: Checks for a proper JDK 11 installation and errors appropriately
- KinD: Checks for `docker` and errors appropriately
- KinD: `cluster loc build` will clone plugin and UI repositories automatically if not present
- Added new processing report dashboard
- Migration for delete policies added
- Indexed metadata added to DeltaFiles.  This metadata can be set by EnrichActions
- Added `getErrorSummaryByFlow` and `getErrorSummaryByMessage` DGS queries
- Added `errorAcknowledged` to error summary filter
- Added `ingressBytes` to DeltaFile and made the field searchable
- Added `stressTest` DGS mutation to facilitate load testing
- Added `errorCause` regex searching to deltaFiles DGS queries

### Changed
- Moved `grafana.*` FQDN to `metrics.*`
- Default MinIO age-off changed from 1 day to 13 days
- Migrations run with `deltafi install`
- DeltaFiles with errors no longer produce child DeltaFiles with the error domain
- Improve performance of deletes
- Changed Graphite performance settings that were causing issues on MacOS
- KinD: Tests are no longer run as part of a `cluster loc build`

### Deprecated
- Use of monotonic counters in graphite metrics is deprecated and will be replaced with delta counters in the next release
- Use of Quarkus is deprecated.  Ingress is the last remaining Quarkus application and will be migrated to Spring Boot in the next release

### Removed
- Error children are no longer created on errored Deltafiles
- Remove minio housekeeping routine due to scaling issues. Depend on minio ttl instead

### Fixed
- KinD: Creates docker kind network automatically if not present on `cluster up`
- KinD: `cluster install` does not require `cluster up` before executing the first time
- KinD: Added missing dependencies to Brewfile
- Turned off a performance optimization that caused issues with Graphite on MacOS
- Requeue would sometimes pick up a file for requeue prematurely, resulting in potential double processing by an Action
- Fixed problem where enrich flow name was being included when searching for enrich action names
- Fixed `deltafi-monitor` cache issue that was causing intermittent API disconnect issues in the UI

### Tech-Debt/Refactor
- Plugins no longer depend on deltafi-core-domain
- Removed core domain dependencies from action kit.  This is a breaking change for plugins
- KinD updated to have a simpler initial user experience

### Upgrade and Migration
- Refactored common and core domain to remove deltafi-core-domain dependencies will require refactoring of all plugins to move to the new common dependencies
- Upgraded Redis to `7.0.4`
- Upgraded Minio to `RELEASE.2022-08-25T07-17-05Z`

## [0.96.4] - 2022-08-04

### Added
- Audit logging capability
- `Audit Logging` dashboard
- `Flow Summary` dashboard
- API: `/me` endpoint to support UI self-identification

### Fixed
- Bug resulting in filtered deltafiles being marked as errors

### Removed
- Removed `actionKitVersion` parameter from plugin

### Tech-Debt/Refactor
- Added optional output directory to plugin

### Upgrade and Migration
- Updated KinD base image

## [0.96.3] - 2022-07-29

### Added
- "black hole" HTTP server that returns 200 responses for any post added.  To enable this
  pod, set the enabled flag in values.yaml.  This is intended as an alternative to filtered
  egress, and allows the full egress path to be exercised
- Debug logging in ingress for all posted DeltaFiles
- Error logging in ingress for all endpoint errors

### Changed
- CLI: `deltafi install` loads variables and flows
- If no egress is configured for a flow, DeltaFiles will be errored instead of completed
- KinD: MongoDB uses a fixed password to allow preservation of the database between clusters
- Egress sink can be disabled in values.yaml

### Removed
- MinIO dashboard link removed

### Fixed
- Flowfile egress deadlock fixed.  The fix is memory inefficient, but will work until a better solution is developed
- Fixed scale factor for bps gauges on Grafana dashboards
- API: System property requests are cached to reduce load against core-domain
- Scheduled deletes are performed in batches to avoid overwhelming the core
- Metrics for flows using the auto resolution of flows now report on the resolved flow instead of 'null'
- `dropped_file` metrics will be incremented for every 400 and 500 error

## [0.96.2] - 2022-07-22

### Added
- KinD: Detect arm/Apple Silicon and load overrides for arm64 compatible Bitnami images
- KinD: `deltafi` CLI wrapper that allows CLI to be used natively with KinD
- KinD: `cluster` command output cleaned up and streamlined
- Pod memory and CPU metrics enabled in the kubernetes dashboard
- `deltafi mongo-eval` command added
- `deltafi` CLI `load-plans` command now consolidates the behavior of 'load-plan' and 'load-variables'

### Changed
- `deltafi uninstall` command removes collections from the deltafi db in mongo
- `deltafi` CLI checks for required tool installation on execution

### Deprecated
- Consumes/produces configuration for Transform and Load Actions

### Removed
- 'deltafi` CLI: Removed `load-plan` and `load-variables` commands

### Fixed
- Parameter schemas will now properly validate with list and map variable values
- DeltaFi CLI checks for dependencies (`jq`, `kubectl`, etc.)
- Nodemonitor fix for long filesystem names
- Null/empty array issue with includeIngressFlows
- Mount the keyStore and trustStore directly (RKE compatability)
- KinD: Fix for starting cluster with a redundant config map
- New base images to deal with FIPS issues

### Tech-Debt/Refactor
- Nodemonitor logging cleaned up, converted to structured logs
- Warnings on Quarkus app startup resolved
- Reduced Loki probe time

### Upgrade and Migration
- Upgraded MongoDB to `5.0.6-debian-10-r0`
- Upgraded Redis to `6.2.6-debian-10-r0`
- Upgraded Minio to `RELEASE.2022-07-17T15-43-14Z`

## [0.96.1] - 2022-07-10

### Added
- Implemented delete configuration interface
- Added index for the requeue query

### Changed
- Default authentication mode is now `basic`

### Fixed
- Fixed configuration related warnings on startup of ingress and action pods
- Implemented workaround for configmap duplication errors during `deltafi install`

## [0.96.0] - 2022-07-07

### Added
- Disk usage based delete policies
- Charts updated to support RKE2 Clusters
- Charts updated to support Longhorn storage
- Introduced node selector tags to indicate roles.  These roles will be used for RKE2 and Longhorn deployments
- Acknowledged errors are deleted upon acknowledgement
- Ingress routing capability
  - Rules engine for assigning DeltaFiles with unspecified flows
  - Ingress routing based on regex matches in filename
  - Ingress routing based on value matches in metadata
  - Rule validation
  - CLI and API support
- Metrics redesign
  - Grafana now installed by default
  - Graphite metric store added
  - Nodemonitor daemonset now responsible for monitoring disk usage
  - Monitor pod pushes queue metrics to graphite
  - Action kit pushes action and error metrics to graphite
  - Default dashboards added for flow monitoring
- Log aggregation redesign
  - Promtail added to collect logs from all Kubernetes pods
  - Loki added to aggregate and store logs
  - Dashboards added to Grafana to provide log viewing and summary
- Added `sourceMetadataUnion` DGS query to return a unified set of source metadata for a set of DeltaFiles
- Removed noisy logging for several core support pods

### Changed
- Retry of a DeltaFile is now referred to as `resume`
- Changed default deltafi-auth deployment strategy to `recreate`
- Migrated to DGS v5.0.3
- Migraded to Jedis v4.2.3
- Minor version updates to various support packages
- Replay and resume of a DeltaFile are prohibited after the DeltaFile content has been deleted
- Config server functionality merged into deltafi-core-domain
- Updated base Spring and Quarkus docker images
- Disabled Redis journaling

### Deprecated

### Removed
- DGS Gateway completely removed (replaced by GraphiQL UI)
- Metricbeat removed, replaced by nodemonitor
- deltafi-config-server removed, functionality migrated to deltafi-core-domain
- Fluentd removed, replaced by promtail
- Elasticsearch removed
- Kibana removed, functionality fully replaced in Grafana and Loki
- Elasticsearch client removed from deltafi-api
- Kibana batch job removed

### Fixed
- NPE with null values for metadata fixed
- Fixed getAllFlows query for smoke/egress-sink to include ingress and egress
- Optimized data copy overhead in FlowfileEgressAction with piped streams

### Tech-Debt/Refactor
- Rubocop cleanup of deltafi-auth codebase
- KinD command line refactored

### Security

### Upgrade and Migration
- New storage and persistent volume claims are required for deltafi-loki, deltafi-graphite, and deltafi-grafana

## [0.95.4] - 2022-06-08

### Added
- Ability to add `SourceInfo` metadata on retry
- Record original filename and flow if they are changed for a retry
- Auth service user management
- Replay capability

### Changed
- Egress flows will not run if `includeIngressFlows` is an empty list

### Deprecated
- DGS gateway deprecated in favor of using GraphiQL UI
- Common metrics API is deprecated.  A new micrometer API is forthcoming backed by Graphite and Grafana

### Removed
- DGS gateway ingress and status checking

### Fixed
- Bug in versions API
- New error DeltaFiles need an empty list of child DIDs

### Tech-Debt/Refactor
- Disable TLS for DinD CI builds (performance optimization)
- Automatically trigger passthrough project on successful builds

## [0.95.3] - 2022-06-08

### Added
- `SSLContextFactory`
- Publishing to maven central for API jars
- Publishing to Gradle plugin central for deltafi-plugin plugin
- Enrichment stage added (between ingress and egress stages)
- Gradle tasks to publish docker images locally (for KinD)
- KinD cluster scripts

### Changed
- Base docker images updated
- NGINX Ingress Controller uses DN for auth cache
- Upgrade to Quarkus platform 2.9.2
- Errors are linked to their originator DeltaFile via bidirectional parent-child links

### Removed
- Zipkin no longer suported
- Token used for git authentication removed from default configuration

### Tech-Debt/Refactor
- Warnings cleaned up

## [0.95.2] - 2022-05-13

### Added
- FlowPlan implementation
- Revamped plugin system
- Gradle-plugin plugin
- Open source license
- FormatMany support
- Action start and stop times in DeltaFile
- JavaDocs for Action Kit
- Queryable DeltaFile flags for filtered and egressed states
- Track total bytes on the top level of DeltaFile

### Changed
- Lombok-ified action kit API

### Deprecated
- Zipkin will be removed in the near future

### Fixed
- Move DeltaFile to an error state when a requeued action is no longer running
- Unhandled exception in RestPostEgress

## [0.21.4] - 2022-04-25

### Fixed
- FlowFile v1 content type corrected for ingress

## [0.21.3] - 2022-04-22

### Added

### Changed
- Config server automatically mounts config map

### Fixed
- Issue preventing DeleteAction retry with onComplete delete policy
- Exception with Redis disconnects
- Unexpected exceptions with timed delete threads

## [0.21.2] - 2022-04-19

### Added
- NiFi Flowfile ingress support
- NiFi Flowfile egress support
- Gradle plugin subproject

## [0.21.1] - 2022-04-07

### Added
- Retries for unsuccessful egress POSTs
- Additional decompression formats added to `DecompressionTransformAction`

### Changed
- ActionKit: `SourceInfo` map-like helpers for getting/setting metadata
- Error flow set to FilterEgressAction by default, instead of REST post
- Support file trees with egress sink

### Fixed
- Monitor Dockerfile fix
- Decompression stream marking bug

### Tech-Debt/Refactor
- API Refactor

## [0.21.0] - 2022-03-29

### Added
- Disk usage check

### Changed
- Updated base docker images
- Add metadata parameter to transform, load, enrich and format actions

### Fixed
- Allow ingress to accept special characters

### Tech-Debt/Refactor
- Process children from split response in batches

## [0.20.0] - 2022-03-28

### Added
- `SplitterLoadAction`
- Disk metrics

### Changed
- Cleaned up actionkit action input interfaces
- Updated base docker images

### Fixed
- Elastic search issues

## [0.19.1] - 2022-03-21

### Changed
- Limit fields returned from MongoDB to those in the DeltaFile projection

### Fixed
- Fix decompress bug with zip and tar.gz files

## [0.19.0] - 2022-03-13

### Added
- `DecompressionTransformAction`
- Content lists
- Reinjection via splitting DeltaFiles into children
- HTTPS support for action configuration

### Tech-Debt/Refactor
- Simplify probes

## [0.18.2] - 2022-03-01

No changes.  Supporting UI release

## [0.18.1] - 2022-02-24

### Added
- `DropEgressAction`

### Fixed
- Support empty content with `ContentStorageService`

## [0.18.0] - 2022-02-17

### Changed
- Updated base images for docker containers
- Media type is explicitly required

## [0.17.0] - 2022-02-04

### Changed
- Version bumps for many dependencies

### Fixed
- Various bug fixes

## [0.16.3] - 2022-01-19

### Added
- Error acknowledgement

### Changed
- Added RoteEnrichAction to the smoke flow

### Fixed
- Premature action start on retry

## [0.16.2] - 2022-01-13

### Added
- Load and save content as strings in the ContentService
- Added plugin registry
- (API) Added content endpoint

### Changed
- No restart for RETRIED terminal actions
- IngressAction added to DeltaFile actions list
- Renamed Action schema classes
- Added `Result` getters

### Fixed
- Add Ingress Action to protocol stack
- EgressFlowConfigurationInput no longer missing EgressAction
- Intermittent bug with git version in gradle (mainly CI affecting)

## [0.16.1] - 2022-01-04

No changes.  UI update only

## [0.16.0] - 2022-01-04
### Added
- New content storage service
- DeltaFile ingress allowed through UI ingress

### Changed
- Gradle base images updated (CI)
- Domain and enrichment functions migrated to Load and Enrich actions
- Retry accepts list of DIDs
- Chart dependencies updated

### Removed
- Removed Load Groups
- Domain and enrichment DGS interfaces removed

### Fixed
- Require that format action completes successfully before validation attempt
- Config server liveliness checks no longer fail on upgrade

### Tech-Debt/Refactor
- Removed Reckon plugin, replaced with local versioning plugin

## [0.15.0] - 2021-12-20
### Added
- API: versions endpoint
- API: Action Queue check
- Hostname system level property
- Freeform sorts for deltafiles
- Publish config server API JAR
- K8S: Liveness/startup probes for pods

### Removed
- Removed action name and staticMetadata parameters from default ActionParameters

### Fixed
- Deduplicated versions on versions endpoint
- Allow empty parameters in flow configurations
- Config server strict host key checking turned off by default
- Ingress is blocked when the API is unreachable

### Tech-Debt/Refactor
- DRY'ed up gradle build files

### Security
- Forced all projects to log4j 2.17.0 to avoid CVEs

[Unreleased]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.2...main
[1.1.2]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.1...1.1.2
[1.1.1]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.0...1.1.1
[1.1.0]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.7...1.1.0
[1.0.7]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.6...1.0.7
[1.0.6]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.5...1.0.6
[1.0.5]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.4...1.0.5
[1.0.4]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.2...1.0.4
[1.0.2]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.1...1.0.2
[1.0.1]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0...1.0.1
[1.0.0]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC8...1.0.0
[1.0.0-RC8]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC7...1.0.0-RC8
[1.0.0-RC7]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC6...1.0.0-RC7
[1.0.0-RC6]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC5...1.0.0-RC6
[1.0.0-RC5]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC3...1.0.0-RC5
[1.0.0-RC3]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC2...1.0.0-RC3
[1.0.0-RC2]: https://gitlab.com/deltafi/deltafi/-/compare/1.0.0-RC1...1.0.0-RC2
[1.0.0-RC1]: https://gitlab.com/deltafi/deltafi/-/compare/0.109.0...1.0.0-RC1
[0.109.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.108.0...0.109.0
[0.108.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.107.0...0.108.0
[0.107.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.106.0...0.107.0
[0.106.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.104.4...0.106.0
[0.104.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.104.3...0.104.4
[0.104.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.104.0...0.104.3
[0.104.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.103.0...0.104.0
[0.103.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.102.0...0.103.0
[0.102.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.5...0.102.0
[0.101.5]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.4...0.101.5
[0.101.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.3...0.101.4
[0.101.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.2...0.101.3
[0.101.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.1...0.101.2
[0.101.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.101.0...0.101.1
[0.101.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.100.0...0.101.0
[0.100.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.8...0.100.0
[0.99.8]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.6...0.99.8
[0.99.6]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.5...0.99.6
[0.99.5]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.4...0.99.5
[0.99.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.3...0.99.4
[0.99.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.2...0.99.3
[0.99.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.1...0.99.2
[0.99.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.99.0...0.99.1
[0.99.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.5...0.99.0
[0.98.5]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.4...0.98.5
[0.98.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.3...0.98.4
[0.98.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.2...0.98.3
[0.98.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.1...0.98.2
[0.98.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.98.0...0.98.1
[0.98.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.97.0...0.98.0
[0.97.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.96.4...0.97.0
[0.96.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.96.3...0.96.4
[0.96.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.96.2...0.96.3
[0.96.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.96.1...0.96.2
[0.96.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.96.0...0.96.1
[0.96.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.95.4...0.96.0
[0.95.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.95.3...0.95.4
[0.95.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.95.2...0.95.3
[0.95.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.21.4...0.95.2
[0.21.4]: https://gitlab.com/deltafi/deltafi/-/compare/0.21.3...0.21.4
[0.21.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.21.2...0.21.3
[0.21.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.21.1...0.21.2
[0.21.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.21.0...0.21.1
[0.21.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.20.0...0.21.0
[0.20.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.19.1...0.20.0
[0.19.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.19.0...0.19.1
[0.19.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.18.2...0.19.0
[0.18.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.18.1...0.18.2
[0.18.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.18.0...0.18.1
[0.18.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.17.0...0.18.0
[0.17.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.16.3...0.17.0
[0.16.3]: https://gitlab.com/deltafi/deltafi/-/compare/0.16.2...0.16.3
[0.16.2]: https://gitlab.com/deltafi/deltafi/-/compare/0.16.1...0.16.2
[0.16.1]: https://gitlab.com/deltafi/deltafi/-/compare/0.16.0...0.16.1
[0.16.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.15.0...0.16.0
[0.15.0]: https://gitlab.com/deltafi/deltafi/-/compare/0.14.1...0.15.0
