# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

All [Unreleased] changes can be viewed in GitLab.

## [2.27.0] - 2025-08-05

### Added
- Added help icon to search page advanced search options annotation field 
- Added tooltips to the topic conditions on Transform Builder
- Added graphql endpoints for managing SSL keys

### Changed
- Use a single ca-chain that can be used for keys

### Fixed
- Fixed Resume Dialogs to close the dialog after a resume request has been submitted and then reload the errors datatable.
- Fixed Filter Page to use time range on the inital loading of data, where before it was only using the time range when refreshing.
- Added the time range params to the "cause message" query.   
- Fixed bug of passing in the wrong flow type to the resumeMatching query in the ResumeBulkActionDialog 

### Tech-Debt/Refactor
- Removed unused permissions: ActionEvent, IngressRoutingRuleCreate, IngressRoutingRuleDelete, IngressRoutingRuleRead, IngressRoutingRuleUpdate, PluginRegistration, TopicsDelete, TopicsRead, TopicsWrite, RegistryDelete, RegistryUpload, RegistryView

### Upgrade and Migration
- Added migration to remove old permissions from `roles` table
- The compose `cert` folder has new structure, `deltafi up` looks for the old setup and automatically migrates any existing key files

## [2.26.1] - 2025-08-01

### Changed
- System plugin variables now act as global values that apply across all flows. When both system and plugin variables exist, plugin variables take priority.

### Fixed
- Timeout issue that kept long running graphql calls from completing in the TUI

## [2.26.0] - 2025-08-01

### Added
- New bulk acknowledge `acknowledgeMatching` GraphQL endpoint allows acknowledge by `DeltaFilesFilter`
- Egress sink can be configured to drop metadata instead of writing to disk as a separate JSON file
- Dirwatcher will respond to DIRWATCHER_SETTLING_TIME environment variable to set the polling period for accepting a file
- Created a new permission `DeltaFilePinning`

### Changed
- File ingress service renamed dirwatcher
- Dirwatcher will periodically sweep watch dirs to pick up files that failed to generate os filesystem events
- Updated the `pin` and `unpin` mutations to requrie the `DeltaFilePinning` permission (was `DeletePolicyDelete`)

### Fixed
- Fixed issue where dirwatcher would prematurely pick up a partial file and publish it

### Upgrade and Migration
- Installer should add the new permission `DeltaFilePinning` to the approprirate roles

## [2.25.4] - 2025-07-24

### Added
- TUI: config command flags
- TUI: Kubernetes orchestration support

### Fixed
- TUI: Do not allow orchestration selections when invalid for the environment
- Fixed retainExistingContent flag in ExtractJson and ExtractXml
- UI: Fixed issue that prevented a refresh on the system map.
- TUI: In compose, UI http and https ports are configurable

## [2.25.3] - 2025-07-21

- No changes for this release

## [2.25.2] - 2025-07-21

### Added
- TUI: Status command renders core version

### Fixed
- Config path fix for compose installations.  Note: 2.25.1 should not be installed for compose orchestrated systems.  Upgrades should skip to 2.25.2.
- KinD: Remove unnecessary registry port mappings

### Upgrade and Migration
- Patched dependencies for CVE resolution
- Upgrade to Go 1.24.5
- Rolling back gradle-plugin gradle-docker plugin to 0.36.0 due to build compatibility issues
- Update  Python `json-logging` module to 1.5.1 to match new base image `deltafi/python:3.13.5-1`

## [2.25.1] - 2025-07-18

### **NOTICE**
- A configuration bug was introduced in this release that will result in a compose orchestrated system being unable to read from the Postgres database.  Upgrade to 2.25.2 to avoid this issue.

### Added
- TUI: Full KinD support for deployment and core development modes
- TUI: `view` subcommand for `deltafiles`, `filtered`, and `errored` commands
  - Interactive browser for viewing files with search criteria similar to `deltafi search`
- TUI: `list` subcommand for `deltafiles`, `filtered`, and `errored` commands
  - List search results in table, JSON, or YAML format
- TUI: `search` command flag `--auto-refresh` for continuous monitoring
- TUI: `search` displays total files
- TUI: `deltafi upgrade changelog` command will render changelogs for specific versions or for all versions newer than the current version that is installed

### Changed
- TUI: `search` command - Page-down on last page will advance to last row

### Fixed
- TUI: Search command page-down and arrow down on last page bug

## [2.25.0] - 2025-07-14

### Added
- Added time search parameters to the Error and Filter Pages 
- [integration test framework] When using `ExpectedContentData`, the expected value may now be base64 encoded, and also supports additional substitutions using RegEx in `extraSubstitutions`
- TUI: `integration-test` command
- TUI: topic command

### Fixed
- Fixed core actions docs not showing in public docs
- Fixed issue in colorized TUI JSON output where escaped strings were unescaped

### Upgrade and Migration
- *NOTE: POTENTIAL METRICS LOSS!*
  - A previous bug that was not maintaining the `event_annotations` analytics table correctly might leave the system with an excessively large table that cannot be pruned in a timely manner by the cleanup task. A migration was added to truncate the table, which could cause metrics to be lost for any In-flight DeltaFiles when upgrading. To avoid losing metrics for In-flight DeltaFiles, disable ingress when upgrading, and allow all DeltaFiles to complete before starting the upgrade. Be sure to re-enable ingress after the upgrade is finished.
- Bump to the latest KinD patch release

## [2.24.0] - 2025-07-08

### Added
- Added drill-down links from the Dataflow Analytics dashboard to the Error and Filter Analysis dashboard
- Added On-Error Data Sources, a new type of data source that automatically triggers when errors occur in other flows
- Added filtering options for On-Error data sources:
  - `errorMessageRegex` - Filter by error message patterns
  - `sourceFilters` - Unified filtering structure with optional fields:
    - `flowType` - Filter by source flow type (REST_DATA_SOURCE, TIMED_DATA_SOURCE, TRANSFORM, DATA_SINK)
    - `flowName` - Filter by specific flow name
    - `actionName` - Filter by specific action name that generated the error
    - `actionClass` - Filter by action class for cross-flow error monitoring
  - `metadataFilters` - Filter by source DeltaFile metadata key-value pairs
  - `annotationFilters` - Filter by source DeltaFile annotation key-value pairs
  - `includeSourceMetadataRegex` - Include matching metadata from source DeltaFile in error DeltaFile
  - `includeSourceAnnotationsRegex` - Include matching annotations from source DeltaFile in error DeltaFile
- Added GraphQL mutations for creating and managing On-Error data sources
- Added documentation explaining On-Error data source configuration and use cases with detailed filtering examples
- TUI: Added command support for on-error data sources
- TUI: `graphql` command to execute GraphQL queries against the DeltaFi GraphQL API.
- TUI: `deltafi postgres status` - Renders status information on the primary Postgres database
- TUI: `deltafi postgres migrations` - Lists flyway migrations for the primary Postgres database

### Changed
- Add filter to Error Analysis dashboard to hide items that were filtered due to Missing Subscribers
- Add filter to Filter Analysis dashboard to hide items that were filtered due to Test Mode 
- Child DeltaFiles now inherit the depth of their parent flow (parent depth + 1)
  - The existing `maxDepth` configuration now prevents infinite recursion across spawned children, not just within a single DeltaFile
- Revert raw analytic data storage to 3 days 
- TUI: data-source load-timed and load-rest consolidated to single load command

### Fixed
- Boost the `event_annotation` clean-up batch size during start-up to 1000
- Fix failing method used to clean up event annotations table
- TUI: Fixed data source get and load commands to be symmetrical
- TUI: Fixed extraneous output on `deltafi postgres cli` exit
- TUI: Allow piping from `deltafi postgres eval` and `deltafi minio mc` commands

### Upgrade and Migration
- Database migration adds support for OnError data source configuration fields (negligible migration time)
- Upgrade python dependencies to match new base image: deltafi/python:3.13.5-0
  - pydantic 2.11.7
  - requests 2.32.4
  - urllib3 2.5.0
  - pytest 8.4.1

## [2.23.0] - 2025-06-23

### Added
- Added resume by error cause and resume by flow options to the GUI 
- Added the ability to query deltafiles by "Modified Date" or "Created Date" on the Search Page.  
- Added rate limiting support for REST data sources
  - Configure limits for FILES (number of files per time period) or BYTES (data volume per time period)
  - GraphQL mutations `setRestDataSourceRateLimit` and `removeRestDataSourceRateLimit` for managing rate limits
- Java plugins will have the task `install` which will build the plugin docker image locally and install it on a locally running DeltaFi

### Changed
- Dataflow Analytics + Error/Filter Analysis grafana dashboard facelifts - remove grid lines, set opacity to zero, remove transparent background 
- When performing a DeltaFiles - Search using annotations, the following options are now supported in the annotation search value (Gitlab-467):
  - If the value is `*`, the query will only check that the key exists (matches any value)
      - E.g., `*` will match `null`, `"""` (empty string), or `abcd`
  - If the value contains `*`, the query will match if the value is LIKE the provided value, treating `*` as a wildcard
    - E.g., `abc*3` will match `abc123`, but not `abcdef`
  - If the value starts with `!`, the query will add a `NOT` to the comparison
    - E.g., `!abc*3` will match `abcdef`, but not `abc123`
  - When an `!` or `*` is not present, the default behaviour remains an exact match
    - E.g., `abc` will only match `abc` 
- Reduce window for analytic annotation changes from 3 days to 1 day. Any annotations added within 1 day of an ingress event will be applied to that ingress event.
- Analytics Grafana charts now refresh continuously instead of waiting 5 minutes between updates
- Egress-Sink: Updated error message when DeltafiMetadata HTTP header is not found

### Fixed
- Improve appearance of zero values in analytics grafana charts
- Update the Action Kit Version of the System Plugin to match the DeltaFi version
- Improvements to the TUI command help and command grouping
- TUI: Kubernetes `up` command will now respect the site `values.yaml` file

### Tech-Debt/Refactor
- Clean up JUnit dependencies to use BOM
- Updates to the documentation
  - New installation procedures for compose systems
  - General cleanup
  - TUI documentation

## [2.22.1] - 2025-06-16

### Added
- Added to the `ExpectedContentData` for integration tests the options to `ignoreWhitespace` and `macroSubstitutions`

### Changed
- Replace `HttpService` with `OkHttpClient` for core egress actions to deal with thread pooling issues

## [2.22.0] - 2025-06-16

### Added
- Added a search/filter feature to the Content Viewer file list.
- New `ValidateMetadata` action offers a way to validate required metadata
- Add pagination for events. 
- Updated default python builds  to python 3.13.3; python 3.12 is still supported
- The `resumeByErrorCause` mutation now accepts a list of ResumeMetadata
- TUI: Added minio commands: exec, mc, watch
- TUI: Development modes will now set up ports for local development automatically
- TUI: `set-admin-password` command
- TUI: Upgrade command

### Changed
- Made the Content Viewer preview size a system property (`uiContentPreviewSize`) and set the default to 32KB.
- Include the `FlowType` in the `PerActionUniqueKeyValues` GraphQL type
- The `resumeByFlow` now takes a list of ResumeMetadata instead of a single ResumeMetadata object
- TUI: Dashboard can terminate with q or esc keys, and refresh with space bar
- TUI: `config` command is now a wizard for system configuration
- TUI: Use semantic versioning rules to warn about upgrades and downgrades
- TUI: Up command will always write out new version to config

### Fixed
- Fixed a bug in the Content Viewer that caused the file list to scroll off the screen when viewing many files.
- Fixed a bug that caused the `/api/v2/config` API to always report `useUTC` as `true` regardless of how the `uiUseUTC` system property was set.
- Fixed a bug in the pythin action kit that was ignoring thread scaling values

### Removed
- TUI: `init` command removed, functionality now in `config` command

### Tech-Debt/Refactor
- TUI: Cleaned up styling for error and warning logging
- Show error code and message on failed GUI upload 

## [2.21.0] - 2025-06-03

### Added
- Added lazy loading of resume/replay metadata 
- Add property `metadataDiskSpacePercentThreshold`. If DB size exceeds this % DeltaFiles will be deleted. 
- Added a `restartPlugin` mutation for restarting plugins
- Added TUI commands for managing delete policies 
- Added a minio cli command to the TUI  
- TUI: `deltafi plugin describe` command
- TUI: `deltafi search` command added to list and search deltafiles
- Added valkey cli, stat, monitor and watch commands to the TUI 

### Changed
- Changed the egress-sink deployment to use a RollingUpdate strategy instead of Recreate
- diskSpacePercentThreshold can now hold decimal values
- TUI: Improved markdown rendering

### Fixed
- TUI: ingress command should only tab complete data-sources that are running

### Tech-Debt/Refactor
- Updated Python base image to 3.12.10
  - pydantic to 2.11.5
  - redis to 6.2.0
  - pytest-mock to 3.14.1

### Upgrade and Migration
- Downgraded jacksonVersion from 2.19.0 to 2.18.4 to fix fabric8 kubernetes client issue  

## [2.20.0] - 2025-05-27

### Added
- Added metadata and annotation configuration to Data Source Configuration Dialog. 
- Added the system build version (`version`) to the REST `api/v2/status` JSON output

### Changed
- Changed the Publish Cell UI to remove duplicate topics and to show default topics. 
- Change deltafi-core-actions HttpClient from HTTP 2.0 to 1.1

### Fixed
- Fixed Data Source, Transform, Data Sink dialogs not updating on save
- Added ErrorAcknowledged filter to error summary queries 
- Ensure HTTP egress response bodies are closed even if the body is not read on success

### Tech-Debt/Refactor
- Improve auto resume query performance 
- Speed up event annotation cleanup 
- Improve in-flight query speed 
- Improve performance of query checking to see if actions are still cold queued
- Remove unnecessary left joins 

### Upgrade and Migration
- Java dependency updates:
  - dgsVersion=10.1.2
  - jacksonVersion=2.19.0
  - jsonschemaGeneratorVersion=4.38.0
  - jupiterVersion=5.12.2
  - lombokVersion=1.18.38
  - minioVersion=8.5.17
  - mockitoJupiterVersion=5.18.0
  - springBootVersion=3.3.11
  - testContainersVersion=1.21.0
- Upgrade to Go 1.24.3

## [2.19.0] - 2025-05-20

### Added
- Added the ability to import and export flows from the Data Source, Transform, and Data Sink Pages 
- Added Linux/arm64 to the distribution architectures

### Changed
- The HttpEgress action now includes the `Content-Length` header in the request

### Fixed
- Bash bug in nodemonitor doit.sh that occasionally generates an error upon startup
- Fixed compose user for deltafi-node-fastdelete and deltafi-core-actions
- Made compose user/group consistent for all images
- TUI: Dashboard layout fixed

### Tech-Debt/Refactor
- Improve performance of auto-resume 

## [2.18.0] - 2025-05-19

### Added
- Added System map to App  
- TUI: `deltafi dashboard` command
- TUI: default site files will be created automatically if they are missing:
  - site/values.yaml (Configuration parameters)
  - site/compose.yaml (Compose overrides, compose orchestration only)
  - site/compose.plugin-dev.yaml (Compose overrides for plugin development)
  - site/compose.core-dev.yaml (Compose overrides for core development)
- TUI: `data-source list` lists the states of all actions that are downstream from the data source
- TUI: `data-source` state subcommands (start/stop/pause) will take a `-a`/`--all-actions` flag that will apply the state change to all actions that are downstream from the data source
- TUI: `deltafi properties` command supporting `list`, `get`, and `set` subcommands

### Fixed
- Compose stack: nodemonitor needs local user and group to access file system
- Compose secrets need to be quoted to prevent `$` character from being misinterpreted
- FIX: Compress and Delay actions were needlessly republishing metadata 
- TUI: `deltafi data-source get PLAN` needed a format flag to correctly display results
- Restore deprecated HTTP egress parameters to maintain backward compatibility. 
- TUI: Init command respects new value of deployment mode when it is changed

### Removed
- Removed Disk Space delete policies and replaced them with a single system property, `diskSpacePercentThreshold`, to simplify configuration

### Tech-Debt/Refactor
- Handle core endpoint client resets and SSE session creation race issues, do not log stack trace unless logger is configured for DEBUG mode. 
- Propagate original exception to custom HttpExceptions 
- Move cleanup of `event_annotations` table to a stored procedure to prevent paging.
- Compute flow input content and metadata instead of storing it. 
- Remove republishing of metadata from egress action.
- TUI: `deltafi status` command refactored for usability

### Upgrade and Migration
- This upgrade removes Disk Space delete policies. The `diskSpacePercentThreshold` system property will be set with the minimum global (i.e. no data source set) maxPercent found in the enabled Disk Space delete polices.

## [2.17.0] - 2025-05-12

### Added
- Added a `pinMatching` mutation that pins all complete DeltaFiles matching a given `DeltaFilesFilter`
- Added an `unpinMatching` mutation that unpins all pinned DeltaFiles matching a given `DeltaFilesFilter`
- Added a pinned flag to the `DeltaFilesFilter` to allow filtering by the pinned value
- Added an option to search by pinned on the DeltaFile Search page
- Compose: deltafi-core-worker can be added via site/values.yaml:
```yaml
---
deltafi:
  core_worker:
    enable: true
    replicas: 1 # Or as many instances as you want
```
- Compose: deltafi-core-actions can be scaled via site/values.yaml:
```yaml
---
deltafi:
  core_actions:
    replicas: 2 # Or as many instances as you want
```
- TUI: HTTP client allows internal DNS mapping to remove the need for changes to /etc/hosts
- TUI: `site/compose.yaml` can be used to override or extend the compose orchestration
- TUI: Compose orchestration creates required data directories and creates the Docker DeltaFi network if it does not exist
- TUI: Compose orchestration automatically detects and configures local user ID, group ID, and docker group ID
- TUI: Ingress command has a progress bar
- TUI: Ingress command will upload multiple files concurrently

### Changed
- Compose: Docker UI now uses `/orchestration` path instead of `orchestration.<fqdn>`
- TUI: Compose orchestration no longer needs to place domains in /etc/hosts
- UI sidebar links to new Docker Dashboard location
- TUI: No dependencies on legacy CLI or compose scripts for operation of compose stack
- When configured for local storage, perform minio deletes directly on the filesystem 

### Fixed
- When registering a plugin that has the same version but has flow plan changes, the hMD5 hash now correctly detects those changes
- Retry was not pulling the correct metadata for the Modify Metadata option
- Fix bug where egress metrics were not recorded when awaiting a read receipt 
- Remove internal retry from Http egress actions, fixing stream reuse issues. External Auto Resume should be used instead. 
- TUI: Container based builds in the docker environment

### Removed
- CLI: Removed `_api_v1` deprecated function

### Tech-Debt/Refactor
- Cleanup the Nginx internal mapping used for the CLI
- Added default properties for connecting to MinIO and Valkey

### Upgrade and Migration
- Compose docker-web-gui updated to 1.0.2-1
- HttpEgressParameters and DeltaFiEgressParamters removed: retryCount, retryDelayMs

## [2.16.0] - 2025-05-06

### Added
- Added ability to ACK ERRORS from the Search Page 
- Added an `annotateMatching` mutation that annotates all DeltaFiles matching a given `DeltaFilesFilter`
- Added a `cancelMatching` mutation that cancels all in-flight DeltaFiles matching a given `DeltaFilesFilter`
- Added a `replayMatching` mutation that replays all DeltaFiles matching a given `DeltaFilesFilter`
- Added a `resumeMatching` mutation that resumes all errored DeltaFiles matching a given `DeltaFilesFilter`
- Added `AnnotationConfig` to rest and timed data sources which allows annotations to be added to the DeltaFile by core directly from the flow configuration if not already present
- Added `Metadata` to rest and timed data sources which allows metadata to be added to the DeltaFile by core directly from the flow configuration if not already present
- Added check for client/server clock skew
- Added a `system-flow-plans` command for importing and exporting system flow plans

### Changed
- Change pods/containers to run as non-root user: nodemonitor, clustermonitor (GitLab-449)
- System Properties edit dialog now respects dataType and displays the input associated with that dataType. 
- Improved lazydocker integration with compose via `deltafi compose status` command
- Updated the descriptions for the `actionExecutionTimeout` and `actionExecutionWarning` system properties

### Fixed
- Set chunk times on analytic tables for timely removal of old chunks 
- CHANGELOG underscores wrapped
- Fixed an issue where the action parameters were defaulting to an immutable map causing an error setting default parameter values
- Prevent Delete Policy names from using `=` or `;` because those characters are prohibited from metric tags; also ensure that `minBytes` is not negative
- Fixed list-plugins command
- When saving or importing delete policies that contain errors only detected by the deltafi-core, alter the user on the UI and present the errors

### Upgrade and Migration
- [deltafi-python] Update dependencies per new Python base image: deltafi/python:3.12.9-1 (GitLab-449)
- [core-actions] Bump version of core-actions image for new base image (GitLab-499)
- [deltafi-core] Upgrade deltafi-core-scheduler to newest base image version and configure to run as non-root user (GitLab-449)
- Minio upgrade to RELEASE.2025-04-22T22-12-26Z
- Minio MC upgrade to RELEASE.2025-04-16T18-13-26Z
- Valkey upgrade to 8.1.1
- Promtail upgrade to 2.9.14
- Loki upgrade to 2.9.14
- [Kubernetes] Postgres/Spilo upgrade to 16-3.3.6-2
- [Compose] Postgres/Timescaledb upgrade to 2.19.3-pg16-0
- [Compose] NGINX upgrade to 1.28.0-alpine3.21
- [Compose] Docker web GUI upgrade to 1.0.2-0
- Upgrade Grafana to 11.6.1
- Upgrade to Java 21.0.7
- Upgrade to Go 1.24.2

## [2.15.1] - 2025-04-23

### Fixed
- Fixed cleanup of `event_annotations` table. Deletes were not being properly triggered. Added maintenance job to regularly clean up rows.
- Fix issue where a closed stream could be reused by multithreaded HttpEgress
- Fixed an issue where adding null default values to the action parameters caused the schema validation to fail
- Fix documentation for survey API

## [2.15.0] - 2025-04-18

### Added
- [deltafi-core-actions] New Decompress parameter `passThroughUnsupported` allows content to pass through Decompress without an error if a plain text, unsupported, or unknown format is detected. Sets metadata key `decompressPassthrough` to `true` in this circumstance (GitLab-432)
-  [deltafi-core-actions] Decompress action has new optional parameter `maxExtractedBytes` and metadata override key `disableMaxExtractedBytesCheck` to limit the total bytes extracted from an archive before generating an error (GitLab-434)
- Added the plugin image and pull secret to the snapshot
- Added content selection by tags to content-selecting actions.
- Added an interface, `SchemaGeneratorConfigCustomizer`, that plugins can implement to customize the action parameter schema generator as needed
- Added an ActionOptions argument to Action constructors that is used to generate detailed documentation
- Add an `actionClass` field to the actions stored in DeltaFileFlows

### Changed
- Changed the snaphot restore behavior so it now installs plugins based on the snapshot and uninstalls any plugins that were not in the snapshot
- Changed the exception handling when a failure occurs to save/write content to put the content name in the errorContext instead of the errorCause for better error grouping
- Changed the errorContext generated by ErrorResultException to include the original exception details
- Updated the cache to write cold queued DeltaFiles to disk immediately and remove them from the cache to prevent conflicts when the scheduled cold queue task runs
- Changed the Decompress maxExtractedBytes parameter to a `DataSize` instead of long
- Increase action threads for HttpEgress; put RestPostEgress back to default since it is deprecated

### Fixed
- [orchestration] Fix cluster script to install a plugin using image vs. coordinates (GitLab-456)
- Fixed an issue where syncing the cache to disk could save outdated copies of the DeltaFile, causing unexpected action exceptions
- Fixed an issue where the ActionInput created by the requeue of cold queued DeltaFile was still marked as cold queued preventing it from writing to valkey
- Fixed an issue where the StateMachine was using the action name instead of queue name (action type) to check if the next action should be cold queued
- Fixed an issue where too many DeltaFiles could be moved from cold to warm, exceeding the max queue threshold
- Fixed Merge action docs not displaying in public documentation
- CLI: Pre-upgrade snapshot now correctly checking for the deltafi-core-scheduler pod
- [deltafi-core-actions] Update the mediaType for the `Format.TAR_Z` to `application/x-compress` to match TIKA
- Save child DeltaFiles before saving the completed parent DeltaFile to avoid an edge case where the completeParents process could update the parent before the children were persisted
- Ensure analytic annotation value lookup table entries are unique to prevent race condition where multiple entries would cause lookups to fail and analytics to be disrupted

### Tech-Debt/Refactor
- Various database optimizations for analytics
- [deltafi-core-actions] Clean up `Decompress` code to utilize `Format` enum values

### Upgrade and Migration
- Action descriptions may be replaced with ActionOptions to provide details in generated documentation.

## [2.14.0] - 2025-03-26

### Added
- Add a field named `dataType` to the properties to specify the property data type
- [deltafi-action-kit-test] Added `hasContextContaining()` methods to ErrorResultAssert and FilterResultAssert
- HttpFetchContent - added a parameter named `headersToMetadata` that enables storing specified response headers as metadata entries
- HttpFetchContent - added a parameter named `filenameMetadataKey` that enables storing the detected filename as metadata with a specified key

### Changed
- Changed core deployment strategy type to 'Recreate'
- Updated smoke-test-data-sink to use the newer HttpEgress action type

### Fixed
- Now add file Content-Type if null on file upload.
- Now wrap long description text in flow builder
- Fixed issue not putting the time to end of day when allDay isnt selected.
- Delete unused rows from `event_annotations` table
- Fix rendering of summary table on Dataflow Analytics
- Fix bug where event group annotations weren't properly preserved when processing multiple annotation events for the same DeltaFile

### Tech-Debt/Refactor
- Improve performance of DeltaFiles queries containing annotation criteria

### Upgrade and Migration
- Updated the base images used in CI
- Compose: Upgrade to deltafi/timescaledb:2.19.0-pg16
- Kubernetes: Upgrade to deltafi/deltafi-spilo:16-3.3.6-1 (Including timescaledb 2.19.0)

## [2.13.0] - 2025-03-19

### Added
- Added ingress metrics for child deltaFiles
- Added filterable analytic ingress type of `DATA_SOURCE`, `CHILD`, or `SURVEY` to analytic dashboards.
- [deltafi-core-actions/DeltaFiEgress] Add extra HTTP headers when sending to the local DeltaFi, and optionally automatically determine the ingress URL
- Added a new RuntimeException, `ErrorResultException`, into the Java action-kit, which is mapped to an `ErrorResult` by the `ActionRunner`
- Added a new RuntimeException, `FilterResultException`, into the Java action-kit, which is mapped to an `FilterResult` by the `ActionRunner`

### Changed
- External paths can be provided for the compose environment and secrets directories

### Fixed
- Set content length in egress sink response
- [deltafi-core-actions/DeltaFiEgress] Fix the data source HTTP header name
- Fix bug where no more than 2 options could be chosen in variables dropdowns for DataFlow Analytics, Error Analysis, and Filter Analysis dashboards
- Publish filtered analytics when a DeltaFile is filtered because it was in test mode

## [2.12.0] - 2025-03-15

### Added
- Add Filter Analysis grafana dashboard

### Tech-Debt/Refactor
- Overhaul analytics to improve performance
- Replace Data Source by Annotation and Pivot by Annotation dashboards with Dataflow Analytics grafana dashboard

### Upgrade and Migration
- Old analytic metrics data will be deleted
- Custom grafana dashboards built with the old data model will no longer work
- Set values for configutation properties allowedAnalyticsAnnotations and analyticsGroupName. See analytics documentation for more details

## [2.11.0] - 2025-03-14

### Added
- Added an `insertBatchSize` property to configure the number of DeltaFiles to insert at once
- Added a method to ContentInput to get content by name
- Added an option to ModifyMediaType to detect by name only
- Added partition and node-specific disk metrics for minio and postgres
- Compose: TimescaleDB with `pg_squeeze` extension
- Testing: Using custom timescaledb with `pg_squeeze` extension

### Changed
- System Properties are now edited in a Dialog and can better handle larger values
- DeltaFileCacheService now uses bulkInsert for new DeltaFiles from the saveAll method
- Automatically reclaim space in `delta_files`, `delta_file_flows`, and `annotations` tables when dead records reach `>20%` of total table size using `pg_squeeze`

### Fixed
- Fixed issues with permissions causing cloned or created timed-data-sources not to appear in Data Sources Page
- Remove DeltaFile from the cache when an unhandled exception occurs to prevent inconsistent state from being saved
- Fix a race condition where missing or inactive data sinks were not properly handled
- Fix an issue that would prevent a DeltaFile from completing other transforms when one forked transform has errored
- Recheck delete policies after each batch to ensure they are still running
- Remove DGS queues when they have not written a heartbeat in over 5 minutes
- Have existing disk metrics check /data/deltafi instead of /data, since these can be on different partitions

### Tech-Debt/Refactor
- Increase speed of deleting DeltaFiles when there is no content present
- Remove unused action number
- Add compression to actions column in `delta_file_flows`

### Upgrade and Migration
- Update python dependencies to match new base image deltafi/python:3.12.9-0
- Postgres spilo base image upgrade
  - `pg_squeeze` 1.8 (new)
  - timescaledb 2.18.2 (from 2.14.2)
  - patroni 3.3.6 (from 3.3.3)
- Upgrade to GraphQL DGS version 10.0.4
- Upgrade to Spring Boot 3.3.6
- Small patches appled to various dependences for CVE cleanup
- Upgrade timescale version in compose to 2.18.2
- Migrated to using custom timescaledb image: deltafi/timescaledb:2.18.2-pg16

## [2.10.2] - 2025-03-04

### Added
- File Ingress Service, a directory watcher for ingress, added to compose stack

## [2.10.1] - 2025-03-03

### Added
- Added asterisks to labels that require values in JsonRenderers

### Changed
- `MINIO_API_DELETE_CLEANUP_INTERVAL` changed to 30s
- The HttpEgress action now includes the `Content-Length` header
- Clear all PV data on KinD cluster reinstall

### Fixed
- Fixed issues with Acknowledging, Annotating, Replaying, and Resuming Dids
- Updated UI to use correct permissions when editing and deleting flows
- Corrected cluster command to default to CLUSTER mode
- [cli] load-system-plans was inadvertently expanding the `*` in the cronSchedule value of timed data sources
- Fix postgres issues with KinD cluster reinstalls
- [cli] When loading a single system plan using `load-system-plans`, drop the "sourcePlugin" element if present. Allows using with output from `export-data-sink-plan` and other similar export commands
- Allow multiple minio CLI clients to connect in Kubernetes
- Handle null appNames when sorting LongActionDetails for the long running actions monitor check
- Postgres logs to stderr for viewing in docker logs
- Relax requeue query criteria to pick up DeltaFiles that are not in the expected state
- Fixed bug causing an error when using the Refresh button on the Search page
- [cli] export-system-plans was missing `join` data for transform actions
- [cli] Fixed load-system-plans capitalization error
- Permissions "FlowPlanDelete" and "FlowPlanCreate" were missing from the "Flows" role category

### Tech-Debt/Refactor
- Consolidated FlowPlanDatafetcher save*Plan methods. Moved logic to PluginService.
- cluster command checks if images need to be synced to KinD before syncing
- Postgres query and index optimizations

### Upgrade and Migration
- Refresh clustermonitor base image and kubectl (1.32.2)
- Grafana upgraded to 11.5.2
- Upgrade Minio to RELEASE.2025-02-18T16-25-55Z
- Upgrade Minio client to RELEASE.2025-02-15T10-36-16Z
- Base image upgrades for Java apps and nodemonitor
- Upgrade Java to 21.0.6

## [2.10.0] - 2025-02-20

### Fixed
- Ensure postgres is pinned to the storage node
- Use the configured PV instead of using local storage

## [2.9.0] - 2025-02-20

### Added
- New system property `actionExecutionWarning` changes the Long Running Tasks check from normal to warning for times meeting this threshold (GitLab-420)
- [python-action-kit] Plugin class now supports a `thread_config` map which can be used to scale action threads

### Changed
- [core-actions] Renamed `ExtractContent` action to `ContentToMetadata`

### Fixed
- Console nextSibling error no longer appears when the DialogTemplate Component is used.
- Fixed bug where we weren't flattening DID array while acknowledging errors on Errors page
- Fixed a bug where saving a system flow plan would overwrite existing plugin flows with the same name

## [2.8.0] - 2025-02-18

### Added
- [core-actions] New `ExtractContent` action writes first content to metadata

### Changed
- Combined error cause and context on the same tab on Error Viewer Dialog
- CLI: Small tweak to allow default compose behavior if cli config file is missing

### Fixed
- Fixed row expansion in Datatables using nested row expansion

### Tech-Debt/Refactor
- Added some forward compat superficial changes to CLI to support TUI

## [2.7.1] - 2025-02-13

### Changed
- Changed the `ActionExecutionTimeout` to bounce a specific pod instead restarting the entire deployment
- Only register a new or upgraded plugin once; it does not need to be repeated for every pod or when pods restart

### Fixed
- No longer populates Data Source on Create Auto Resume Rule dialog for summary pages
- Fixed Search Page Calendar CSS after upgrade to Vite
- Fixed a bug where events generated from the GrafanaAlertCheck and IngressStatusCheck were missing timestamps preventing them from being found for display
- Fix unsafe threading model when actionThreads is set to >1 in the application.yaml
- Replay checks whether first flow is in still test mode, instead of blindly assigning the old state, except in cases where a child was replayed, in which case the test mode will be carried forward
- [core-actions] Updated the Decompress action to use the `long` data type for all archive file size checks

### Upgrade and Migration
- Added `registration_hash` column to `plugins` Postgres table

## [2.7.0] - 2025-02-10

### Added
- Added support for Content-Type override on Upload page
- [deltafi-core] Create an event when a plugin is restarted due to an action execution timeout (GitLab-415)

### Changed
- Set the `kubectl.kubernetes.io/default-container` annotation on the `core-scheduler` and `core-worker` pods

### Fixed
- Fixed sorting on the Errors and Filtered pages.
- [deltafi-core] Rebuilding flows (e.g., after a plugin variable update) now properly clears a flow's INVALID state
- Validate analytic events for required fields to prevent database insert exceptions
- Processing Report now showing file counts instead of byte counts in the "Processed Files by Data Source" table
- Fixed the long-running actions check bug where the same execution time was used for all the dids under an action.
- Fix null pointer exception on requeue of malformed DeltaFiles

### Tech-Debt/Refactor
- Increase timescaleDB chunk size from 1 day to 1 week to ensure typical queries use a single chunk for better performance
- Improve Annotation by Data Source and Pivot by Annotation dashboard performance

### Upgrade and Migration
- To change the timescale chunk size the tables must be recreated and migrated. This can be a lengthy migration depending on the size of these tables.
- `jq`, `yq`, and `lazydocker` are now included in distro files

## [2.6.1] - 2025-02-03

### Changed
- Default time range on processing report changed to last 24 hours
- Updated the descriptions for DeltaFi properties using the `Duration` data type to indicate the ISO 8601 format should be used

### Fixed
- Setting plugin variable values is now `Transactional` so new values will be consistent across flows

### Tech-Debt/Refactor
- Add database indexes to improve counting performance when maxErrors is set

## [2.6.0] - 2025-02-03

### Added
- Added test mode indicator to each flow on DeltaFile viewer
- Add HttpFetchContent transform action
- When clicking a new action in the transform builder it now automatically scrolls to the top of the doc
- Create a new event whenever a flow is changed to an invalid state
- Added the option to use templates in action parameters that pull information from the ActionInput that is sent to the action.  The templates support the Spring Expression Language (SpEL), i.e. you can use things like `{{ deltaFileName.toUpperCase() }}`. The following top level fields are available to use in the templates:
    - `{{ deltaFileName }}` - the deltaFileName of the DeltaFile
    - `{{ did }}` - the did of the DeltaFile
    - `{{ metadata }}` - the metadata from the first DeltaFileMessage
    - `{{ content }}` - the content list from the first DeltaFileMessage
    - `{{ actionContext }}` - the actionContext from the ActionInput being sent to the action
    - `{{ deltaFileMessages }}` - the full list of DeltaFileMessages, useful for joins
    - `{{ now() }}` - helper method to get the current timestamp

### Changed
- The Metadata dialog now renders newlines in metadata values
- Events are now created when the state of a flow is changed (i.e. start/stop/paused)
- Events are now created when test mode is toggled on a flow
- The `Merge` transform action no longer supports the `{{filename}}` placeholder in the filename parameter, it is replaced by the common parameter templating

### Fixed
- Fixed issue with bulk acknowledge on By Message tab of Errors page keeping selected rows even when they've been filtered out.
- Fixed bug preventing test mode message from displaying properly at the top of the DeltaFile viewer
- Search Page Calendar now shows the time saved in the url when navigating back to the page
- Fixed bug allowing unauthorized access to metrics
- Fixed issue where embedded grafana charts did not respect UTC settings in the UI
- Restored TransformResultsAssert#hasChildResultAt(int Predicate<TransformResult>) to fix backwards compatibility
- When replaying a DeltaFile that was created from a ChildTransformResult (e.g., a Split action), the test mode from the parent being replayed is preserved
- Fixed an issue where paused flows would become stopped on upgrades
- Fix bug where contentDeletable flag was not being set on a parent DeltaFile when all children reached a terminal state
- Prevent concurrent updates to the action descriptors map which would often leave flows in an `INVALID` state due to missing actions
- Fixed issue preventing the core from properly routing `/events` and `/unauthorized` to the UI

### Removed
- Removed the `maven-publish` and publishing section from the org.deltafi.plugin-convention to simplify getting started with new plugins

### Deprecated
- Deprecated TransformResultsAssert#hasChildResultAt(int Predicate<TransformResult>)

### Upgrade and Migration
- Plugin projects that used the `org.deltafi.plugin-convention` plugin and require publishing need to add the `maven-publish` to the plugins section of the build.gradle and add the following publishing section:
```
publishing {
    publications {
        mavenJarPublication(MavenPublication) {
            artifact bootJar
        }
    }
    repositories {
        maven {
            url projectMavenRepo
            credentials(HttpHeaderCredentials) {
                name = gitLabTokenType
                value = gitLabToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}
```
- Existing flows that use the `Merge` action need to be updated to use the new templating instead of `{{filename}}`
- Any action parameters that contain `{{ }}` will be treated as templates. If the template contains text that cannot be mapped to a Spring Expression, the parameter will be considered invalid resulting in an error

## [2.5.0] - 2025-01-27

### Added
- Created a new index `idx_delta_files_filtered` to speed up query for the UI Filtered page
- Added tags to content
- Added TagContent action
- Added Content Tags panel to DeltaFile viewer
- Added content tags to DeltaFile content viewer
- Updated documentation for the `HttpEgress` action and parameters
- [deltafi-docs] Add new version of Ubuntu as supported system and tested configuration in public docs
- Add sorting options to errorSummaryByFlow, errorSummaryByMessage, filteredSummaryByFlow, and filteredSummaryByMessage GraphQL endpoints.
- Created a new Egress action, HttpEgress, that will allow for selection of the HTTP request method (POST, PUT, PATCH, or DELETE)

### Changed
- Changed the `passthrough-data-sink` to use the new `HttpEgress` action, with a new plugin variable `passthroughEgressMethod` to choose the HTTP request method
- Mododified the metadata filename created by `deltafi-egress-sink` to include the HTTP request method
- Make `idx_delta_files_filtered` a partial index

### Fixed
- The Decompress action no longer generates an `OutOfMemoryError` when unarchiving a file over 2GB (#401)
- FIX: Data Source by Annotation dashboard overwritten by Pivot by Annotation dashboard because of filename collision
- [deltafi-docs] Fix public docs reference to "topics" for publishing rules in Flows
- Fixed an issue where egress sink failed to write the request body to a file
- Fixed an issue where the blackhole latency argument was not applied
- Fix the ownership of the `egress-sink` data directory when using compose. Also included `graphite` in compose mkdir list
- Test mode reason now shows data source name instead of null when only the data source is in test mode
- Only show `TRANSFORM` action types on the Transform Builder
- Ensure replayed DeltaFiles are paused or placed in test mode appropriately

### Deprecated
- org.deltafi.core.action.egress.RestPostEgress is being deprecated; use org.deltafi.core.action.egress.HttpEgress instead

### Tech-Debt/Refactor
- Create a flow definitions lookup table to normalize flow name and type data in the deltaFile flows table

## [2.4.0] - 2025-01-13

### Added
- [action kit test - Java] New IngressResultAssert for verifying Timed Ingress actions
- Add Pivot by Annotation dashboard
- Added GraphQL endpoints and CLI command support to import/export of flow plans from the system-plugin (those created by the flow plan builders)
  - Export: export-system-plans / getAllSystemFlowPlans
  - Import: load-system-plans / saveSystemFlowPlans
- Added the ability to pin DeltaFiles so they won't be deleted by delete policies

### Changed
-  In compose, use the `.ingress.tls.enabled` setting to determine if NGINX should be setup with the SSL listener instead of automatically setting that up when the key and cert are present.
- [action kit test - Java] TransformResultsAssert now allows content index and annotations to be specified
- Detect extracted content types by file name in Decompress action
- CLI command `load-plans` renamed to `load-system-plans`

### Fixed
- Fixed bug in default format selection in Content Viewer
- Don't delete a parent deltaFile's content if its descendants may still be using it
- Fixed bug on Filtered page when filtered action is not the first action.

## [2.3.1] - 2025-01-08

### Changed
- Updated copyright dates for UI source files

### Fixed
- Fixed old flow wording on test mode confirmations and toast messages

### Upgrade and Migration
- Updated KinD support to Kubernetes 1.31.4 and KinD version 0.26.0

## [2.3.0] - 2025-01-03

### Highlights
- Flows now have the ability to be paused.  A paused flow will suspend processing of DeltaFiles through the flow until the flow is unpaused.  DeltaFile processing will resume normally when the flow is unpaused
- New play/pause/stop user interface for flows

### Added
- Integration tests now evaluate expected metadata for each flow
- Added a `resumeByFlow` mutation that resumes errors for the given flow
- Added a `resumeByErrorCause` mutation that resumes errors with the given error cause
- Added ability to pause individual flows
- The CLI command `run-integration-test` now offers a `--wait` flag that will monitor the running test until it is finished

### Changed
- [UI] Flow on/off switch has been replaced with a play/pause/stop interface
- Updated the license for 2025
- DeltaFiles may be marked COMPLETE when there are pending annotations, but will not be terminal

### Fixed
- Fixed a bug where resuming a DeltaFile from an inactive flow left the DeltaFile in a bad state
- Fixed issue causing UI to become unresponsive after configuring external links.
- Last seen dashboard uses fixed font sizes for stats
- Fixed an issue where clicking on an errored flow from the all errors page or DeltaFileViewer only worked if the error was in the first action instead of the last action
- Annotations in Child TransformResults are now stored in the new DeltaFile
- Read receipts (pending data-sink annotations) are now saved to the DeltaFile

### Tech-Debt/Refactor
- Restructure system snapshots to enable adding backwards compatibility going forward and reduce the amount of changes needed in the CLI and GUI

### Upgrade and Migration
- Upgrade to Grafana 11.4.0
- Upgraded Java base image to `deltafi/deltafi-java-jre:21.0.5-alpine-1`
- The `queued_annotations` table will be cleared

## [2.2.1] - 2024-12-19

### Added
- The integration test framework now supports validation of annotations
- Added a feature that restarts plugins when an action has been executing longer than a configurable duration. To enable the feature you set the new `actionExecutionTimeout` system property, to disable the feature unset the property.

### Changed
- Moved VERSION file to root directory

### Fixed
- Fixed Data Sinks panel dialog not filling tabs
- Update the validation on Auto Resume Policies to require at least one of dataSource, errorSubstring, and action
- Update the validation on Auto Resume Policies to allow a delay of 0 seconds

### Tech-Debt/Refactor
- Improve performance of Data Source by Annotation and Error Analysis dashboards

## [2.2.0] - 2024-12-16

### Added
- Added loading indicators on Errors and Filtered pages.
- Added "eslint-plugin-vuejs-accessibility" to start tracking accessibility issues
- LineageMap (generated by recursive decompress) now allows for map search by fullName attribute
- Setup SSL in nginx when the certs directory is populated
- Added `cert` auth mode support in compose
- Added authentication caching to nginx when running in compose
- Added an optional description parameter to test assertions
- Allow timed ingress actions to produce annotations

### Changed
- Changed the unique constraint on `resume_policies` to be the combination of dataSource, errorSubstring, and action with consideration of absent (null) values
- [deltafi-python/test-kit] Improved message context for assertion errors generated by tests (#341)
- Changed the `cluster` command to check for the `DELTAFI_MODE` before wrapping `deltafi` commands to simplify switching between compose and kind.
- Updated documentation and parameter description for the Decompress action
- Kubernetes: Relaxed failure threshold for default readiness probe
- Result type assertion failures now show details of the incorrect result

### Fixed
- Fixed resume policy constraint to require at least one of dataSource, errorSubstring, or action
- Removed duplicate `type` in RestDataSource flow plan JSON file generated by `deltafi plugin-init`
- Fixed bug where long filenames don't wrap properly and bleed into other columns on Errors Page
- Fixed a bug where bulk resuming could lead to a `StaleObjectStateException`
- Action picker grabber bar now takes up the entire cell
- Fixed on-load race condition on Filtered page.
- Updated the `_plugin_install` method in the compose script to pass the full image to the `deltafi plugin-install` command
- Fixed the audit message for toggling delete policies
- Kubernetes: Added `DATA_DIR` environment variable for SSL configuration
- Kubernetes: Removed core worker cruft left from stateful set to deployment conversion

### Removed
- Removed `actionType` criteria from resume policies (#272)

### Tech-Debt/Refactor
- [deltafi-python/test-kit] Add tests for the test-kit (#341)
- Simplify the Transform Builder Page code removing old and complicated code used in version 1.0

### Upgrade and Migration
- When reverting to an old snapshot, resume policies will not be restored due to constraint changes
- [deltafi-python/test-kit] Python plugin tests checking for specific assertion error messages may require updates; affects tests only, not actions (#341)
- Updated python mockito version to 1.5.3 (was 1.5.1)

## [2.1.1] - 2024-12-03

### Added
- New GraphQL endpoint `forcePluginUninstall` allows for plugin uninstall, even if there are running flows or other blockers (#361)

### Tech-Debt/Refactor
- Optimize dataSource by annotation dashboard queries
- Optimize error analysis dashboard queries

## [2.1.0] - 2024-12-02

### Added
- Python test kit now supports testing of a Timed Ingress action (#347)
- Added `users` and `roles` to the system snapshots
- Added support for YAML file format in python action kit for flows, variables, and integration test files

### Changed
- Redesign of the Action Picker on the Transform Builder

### Fixed
- Fixed the connection to the SSE endpoint when running in compose (#378)
- Fixed filtering by flow on Filtered page. (#379)
- Data Source dropdown on Upload page now only displays REST Data Sources. (#380)
- Compose: Uninstall successfully when there are no plugins
- Fixed a bug where aged off joined DeltaFile parents caused the child to get stuck in flight. The child is now moved to an errored state in this scenario.
- Fix bug where JPA was trying to set the deltaFileId for deltaFileFlows and annotations when already set
- Retain timed data source cron schedule when plugin reregisters if the data source is already running. Only replace it if the data source is stopped or belongs to the system plugin.
- Fix typo in `deltafi uninstall` output

### Tech-Debt/Refactor
- Consolidate start/stop flow endpoints into single setFlowState command
- Change core-worker from a statefulSet to a deployment in k8s

### Upgrade and Migration
- Kubernetes upgrade to this version will require removing the core worker statefulset prior to upgrade:
```
kubectl delete statefulset deltafi-core-worker
```

## [2.0.0] - 2024-11-15

### Highlights
- PostgreSQL migration
  - PostgreSQL has replaced MongoDB for all configuration and DeltaFile retention
  - Postgres CLI integrated in the DeltaFi CLI
- DeltaFi API v1 has been replaced with DeltaFi API v2.
  - The separate `deltafi-api` container is no longer necessary
  - API has been reimplemented in Java
- Full support for Kubernetes and compose orchestration for all 2.0 features
- Flows have been rearchitected for 2.0, including new concepts, a pub-sub architecture, and streamlined action and flow design
- Full support for Kubernetes, KinD, and Compose container orchestration

### Added
- [core-actions] Add simple XML editing/grooming core action (GitLab-211)
- [deltafi-python] Add support to specify empty input and output contents in test kit (#266)
- [deltafi-python] Add documentation and tests for serialization of complex data types in action parameter classes (GitLab-299)
- Added Helper buttons calender refresh to Events page
- Flow Builder joins use supportsJoin logic
- Added the ability to update the Cron schedule inline for datasource.
- Added the ability to bulk cancel DeltaFiles from the Search page.
- New DeleteContent action
- Added maxFlowDepth system property to prevent circular flows
- Added a @Size annotation that will generate minLength and maxLength constraints for action parameters
- Added Markdown documentation for all core transform actions.
- Added Annotate core action (org.deltafi.core.action.annotate.Annotate)
- Integration test CLI, GraphQL endpoints, service, and repo
- Nodemonitor will automatically create application metrics when docker is available.  This will
  allow application metrics in the compose configuration when clustermonitor is not available.
- Python test kit: Added support for action collect mode
- Added Test Mode toggle switch to Egress page.
- Flow Plan Builder now hides and clears the topic field for the default publish rule unless the defaultBehavior is "Publish"
- Added CLI commands `load-integration-test` and `run-integration-test`
- Decompress now supports 7z compression/archive format
- Added a default disk space delete policy that deletes at 80%
- Integration tests are now automatically loaded as part of the plugin registration process. Supported formats are JSON and YAML for Java plugins, and JSON for Python plugins
- Add join field descriptions to the action configuration dialog
- Add `deltafi postgres-cli` implementation in compose
- Added parameters to content-selecting actions that allow selection by exclusion.
- Add index to delta_file_id on delta_file_flows. Was previously only present in a compound index.
- Plugins may now include documentation for their actions by including Markdown files in the docs directory. They may be
viewed by selecting the help icon next to the action name on the Flow Plan Builder.
  - Java:  Include Markdown files with full class names and a ".md" suffix in src/main/resources/docs
  - Python: Include Markdown files with class names and a ".md" suffix in  src/{plugin-dir}/docs
- Added the option to create configuration files for an entity resolver through the `values.yaml` in both kubernetes and compose.
- Add a first pass delete when metadata is being deleted to remove any metadata that does not have associated content (because it was already deleted by another delete policy)
- Added "New Transform" link on Transforms page
- TimescaleDB added to all orchestration systems
- Clickhouse analytic event tables replaced with TimescaleDB/PostgreSQL analytic event tables
- Grafana: PostgreSQL data source added
- Grafana: New dashboards created for annotation and error analytics
- Kubernetes orchestration - added local-path-provisioner scripts to support Postgres storage
- Added a new command, `deltafi configure-plugin-ssl`, for setting up keys and certs for plugins
- Add documentation for configuring SSL in plugins
- Add check for default topic when default publish behavior is PUBLISH
- Added Transforms filter to DeltaFile Search page
- Add a top-level array of unique content object ids that belong to each deltafile to improve delete performance
- Add graphql endpoints for querying topics
- Added Topics page to web UI
- Metrics: Added transform flow level bytes/files in/out metrics
- Added the option to define flow plans in yaml in Java plugins
- Added system plugin flow plans to the system snapshot
- Add topics filter
- Added nesting to the Flows table on DeltaFile Viewer

### Changed
- Increase default number of threads for Rest and FlowFile post egress actions to 4
- Configure the MinioClient with an HttpClient with a connection pool
- Auto Resume remove confirmation should show Name instead of Id
- Sorted Actions and Variables tables in plugins page (315).
- Set system property 'pluginImageRepositoryBase' value for compose environments (#323)
- Clicking on an errored flow on the DeltaFile Viewer or Errors page now shows the error content dialog.
- Changed the Flow Plan Builder to display multi-line Textareas when an action parameter has a maxLength > 80
- Update the monitor k8s resource check to only execute when it is running in a cluster
- Content saved by an action which returns an Error or Filter Result will be deleted by core upon receipt of the associated Action Event
- Core actions changed:
  - org.deltafi.core.action.ingress.SftpTimedIngressAction -> org.deltafi.core.action.ingress.SftpIngress
  - org.deltafi.core.action.merge.MergeContentFormatAction ->
    - org.deltafi.core.action.compress.Compress (.ar, .tar, .tar.gz, .tar.xz, or .zip)
    - org.deltafi.core.action.merge.Merge (binary concatenation)
  - org.deltafi.core.action.CompressionFormatAction -> org.deltafi.core.action.compress.Compress
  - org.deltafi.core.action.ConvertContentTransformAction -> org.deltafi.core.action.convert.Convert
  - org.deltafi.core.action.DecompressionTransformAction -> org.deltafi.core.action.compress.Decompress
    - Added `retainExistingContent` parameter
  - org.deltafi.core.action.DeleteMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
  - org.deltafi.core.action.DeltaFiEgressAction -> org.deltafi.core.action.egress.DeltaFiEgress
  - org.deltafi.core.action.DetectMediaTypeTransformAction -> org.deltafi.core.action.mediatype.ModifyMediaType
    - With `autodetect` parameter set to `true` (default)
  - org.deltafi.core.action.ErrorByFiatTransformAction -> org.deltafi.core.action.error.Error
  - org.deltafi.core.action.ExtractJsonMetadataTransformAction -> org.deltafi.core.action.extract.ExtractJson
    - With `extractTarget` parameter set to `METADATA` (default)
  - org.deltafi.core.action.ExtractXmlAnnotationsDomainAction -> org.deltafi.core.action.extract.ExtractXml
    - With `extractTarget` parameter set to `ANNOTATIONS`
  - org.deltafi.core.action.ExtractXmlMetadataTransformAction -> org.deltafi.core.action.extract.ExtractXml
    - With `extractTarget` parameter set to `METADATA` (default)
  - org.deltafi.core.action.FilterByCriteriaTransformAction -> org.deltafi.core.action.filter.Filter
  - org.deltafi.core.action.FilterByFiatTransformAction -> org.deltafi.core.action.filter.Filter
  - org.deltafi.core.action.FilterEgressAction -> org.deltafi.core.action.egress.FilterEgress
  - org.deltafi.core.action.FlowfileEgressAction -> org.deltafi.core.action.egress.FlowfileEgress
  - org.deltafi.core.action.JoltTransformAction -> org.deltafi.core.action.jolt.JoltTransform
  - org.deltafi.core.action.LineSplitterTransformAction -> removed
  - org.deltafi.core.action.MetadataToAnnotationTransformAction -> org.deltafi.core.action.annotate.Annotate
  - org.deltafi.core.action.MetadataToContentTransformAction -> org.deltafi.core.action.metadata.MetadataToContent
    - Replaced `replaceExistingContent` with `retainExistingContent`. Value must be inverted!
  - org.deltafi.core.action.ModifyMediaTypeTransformAction -> org.deltafi.core.action.mediatype.ModifyMediaType
  - org.deltafi.core.action.ModifyMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
  - org.deltafi.core.action.RecursiveDecompress -> org.deltafi.core.action.compress.RecursiveDecompress
  - org.deltafi.core.action.RestPostEgressAction -> org.deltafi.core.action.egress.RestPostEgress
  - org.deltafi.core.action.RouteByCriteriaTransformAction -> removed
  - org.deltafi.core.action.SmokeTestIngressAction -> org.deltafi.core.action.ingress.SmokeTestIngress
  - org.deltafi.core.action.SplitterLoadAction -> org.deltafi.core.action.split.Split
  - org.deltafi.core.action.XsltTransformAction -> org.deltafi.core.action.xslt.XsltTransform
- Allow blank values in `deltafi system-property set` command
- Allow blank values in the `pluginImageRepositoryBase` property
- Grafana is now accessible via `/visualization`
- Embedded charts in the UI now use `/visualization` instead of subdomain
- Integration test:
  - changed expected delta files to a list
  - changed expectedActions to expectedFlows, and added default state DeltaFileFlowState.COMPLETE
  - require each test configuration have a description, which is now added to TestResult
- Modified the python action kit to track saved content by actions so that it can be deleted by core if the action returns an error or filter result
- Core actions that expect XML media types now accept `application/xml` and `text/xml` by default (using `*/xml` wildcard)
- Upload page trims trailing whitespace from metadata key/value fields
- Headers in the Publish/Subscribe section of the Flow Plan Builder are now capitalized with spaces instead of camelCase.
- Dropdown options in the Publish/Subscribe section of the Flow Plan Builder are now normalized.
- Integration tests are now stored separately from their results with new GraphQL endpoints. This allows for test configurations to be saved once, and run later by using just the name
- Role `IntegrationTestLaunch` renamed to `IntegrationTestUpdate`
- Changed the way the Pub/Sub sections appeared and the functionality within them
- UI: Split the image reference in the plugin install/upgrade dialog into separate fields for image name and tag.
- Store the plugin image name and image tag in separate fields
- Updated Decompress action to support batch content saves to MINIO for the 7z format
- Creating a user with the DN set or updating a user DN will always update the username to the CN
- The `Admin` permission will always be added back to the `Admin` user on restart if it was removed
- Changelog tool will auto git-add when creating or editing the changelog
- Added data source specific CLI commands `timed-data-source` and `rest-data-source`
- Compose: Service port changed to localhost:8042 from 8888
- The monitor checks now run in `deltafi-core`
- Auth requests are sent the `deltafi-core-service`
- Custom entity resolvers are run in the `deltafi-core` and `deltafi-core-worker` pods
- Update the deltaFiles created index to include the dataSource column
- Updated all copyright dates on source headers
- Added additional layer to core UI build in dockerfile to optimize rebuild performance
- Made deltafi-core host the UI and docs
- Add goroutine workers to the egress sink
- Replaced the entity resolver PVC mount with a ConfigMap mount
- DeltaFileStats endpoint sends an exact count of total DeltaFiles for less than 100k files, else it uses the estimate
- Improve query performance by allowing batch fetches
- No longer guarantee order deltaFiles are deleted in.  Previously the removals were sorted by modified date, now deletes will happen in random order, to improve performance.
- [deltafi-core] Update generated plugin's gitlab-ci.yml template
- Look for a header of `DataSource` instead of flow in the ingress endpoint
- Updated field labels and placeholders on New Data Sink dialog
- Updated edit tooltip on Transform Builder page
- KinD: `cluster manifest` changed to `cluster images` to line up with docker and compose commands
- Only schedule the `handleTimedOutJoins` task to run when `schedule.maintenance` is true
- Schedule `handleTimedOutJoins` to run at a fixed delay of one second instead recalculating the next scheduled time
- Reject join configurations with a maxAge below one second
- Moved integration test-related GraphQL types from core to common
- Moved DeltaFileFlowState from core to common
- The Decompress action now supports recursive decompression, with lineage map history/parser
- Unused ("orphaned") content is now handled within the execution of the action that made it rather than in core. Logic
  has been generalized to account for all action event types.
- All orchestration related files have been moved to `/orchestration`
- Install plugins using the full docker image instead of plugin coordinates
- Increased Postgres max connections to 1000 for Kubernetes and compose deployments
- Set the default logging level in the python action kit to INFO (was DEBUG)
- Rename core to core-scheduler
- Rename egress flows to data sinks
- Rename flows to transforms
- On joined files, change data source from "multiple" to "joined:<flowName>"
- Renamed Egress Flows to Data Sinks on DeltaFile Search page
- Content viewer now defaults to showing formatted content when possible
- Increase core hikari postgres maximum pool size to 32
- Increase default startup probe to 3 second checks for 5 minutes (up from 90 seconds)
- Changed startup probe duration for core-scheduler to keep trying for up to 7 hours to accommodate migrations
- Remove survey audit logging
- Create the child DeltaFile `did` from the action kits so the child `did` can be used within the child result
- Metrics: Redesigned and retagged ingress and egress bytes/files metrics
- Convert postgresql varchar fields to text
- Log a warning when no container is found on plugin uninstall instead of returning an error result
- When reverting to a snapshot treat missing flows as informational instead of errors
- Always include system plugin variables in the variables section of the system snapshot
- Only attempt to seed the admin user and roles when `schedule.maintenance` is true

### Fixed
- DeltaFi CLI
  - Fix pub/sub fields in `export-egress-plan` and `export-transform-plan`
  - Updated help/usage to match current command set
- [deltafi-python] Fix test_kit to bypass input/output file reads if empty string content given (#266)
- Fixed errors displayed when installing a plugin on the Plugins page
- Trim groupId, artifactId, and version on Plugins page
- Sorted plugin list in "Select a Plugin" dropdown on Flows page
- Added HTML 'pre' tag to json list renderer to preserve string formatting from backend when displaying descriptions
- Fixed default boolean and integer values not being set in Actions in Flow Builder
- Fixed row height issue with remove button.
- [deltafi-core-actions] Set default parameters in core actions (#284)
- Fixed the Flow Plan Builder Subscribe and Publish sections to allow for saving.
- Fixed bug in Additional Properties Renderer where "[object Object]" appears when a new property is added.
- [deltafi-python] Fix deprecation warning concerning datetime (#296)
- Search Page Calendar Helper now base times off of current time not the time when the calendar dialog was opened.
- Search Page Calendar now shows the time being used every time the dialog is reopened instead of whatever values it was previously closed with.
- Fixed header spacing of buttons and drop downs on several of the pages.
- [deltafi-python] Fix python 3.12 thread compatibility that was affecting the MinIO client (GitLab-300)
- Values selected in the dropdowns in the JSON Renderers can now be cleared out
- Fixed issue in search page calendar looking for data not available on prod builds
- Arrays of integers are no longer being removed from flows on save.
- Added join logic to all actions.
- Added primevue dropdown package to flows page.
- Added DataKey to all DataTables
- Decompress action now batches "save many contents" to MINIO
- Updated deltafile/ingress and deltafi/annotate endpoint to new api/v2 path in docs
- [deltafi-common] Fix order of arguments in ActionContext 'copy' method
- Fixed the CORE_URL when running a plugin locally with `cluster plugin run`
- Removed `property-set` flag in the system-property command (the field is no longer part of the `updateProperties` mutation)
- Fix a bug where updating a system plugin data source would use previous topics and cron schedules instead of the new values
- Fixed the "double authentication" issue when using basic auth
- Lock down Python dependency versions to match what is in the deltafi python base image
- Fix race condition where multiple core/workers could pick up actions from a new DeltaFile and have alternate versions of the truth cached
- In the python action kit, the DID for timed-ingress content saved should match the `IngressResultItem` DID
- Annotation service was incorrectly searching transform flows when restoring expected annotations from a system snapshot
- Fixed potential bug related to default SSL ciphers used in Kubernetes ingress.
- Fixed issue where cloned flows actions override original actions data and cant be removed in the Flow Builder.
- You can now view/search all actions on Flow Plan Builder no matter the number of actions
- Fixed bug with Create Auto Resume Rule on DeltaFile Viewer.
- When ContentResult was created with a content list in the constructor parameters, later attempt to save additional content would throw an immutable list exception
- Updated the scheduled join query to only update unlocked join entries to prevent multiple workers from acting on the same JoinEntry
- Fixed bug of minNum and maxNum not able to be equal.
- Fixed bug of minNum being able to equal to 0.
- Fixed issue where you couldn't save a edited Transform flow in the builder when the edit involves removing an entire action
- Allow unknown users to be resolved by the entity resolver instead of automatically denying access
- Fixed a bug where the java based actions attempted to read all files in the flows directory instead filtering by the extension
- Topics list now properly updates when navigating between pages
- fix minor formatting issue in action runner logs
- CI: `ci` branch creation post orchestration reorganization
- Fix issue where the error message was not rolled up to the flow when adding a circular flow errored action
- Bug in CLI where quoted string arguments would be tokenized by spaces
- Fixed issue with cluster plugin builds not using cluster.yaml
- Warning in clustermonitor Dockerfile fixed
- Compose: MinIO container name fix
- Use the valkey replication mode of primary, master is no longer supported
- Filter DeltaFiles that come through a DataSource with test mode enabled
- Updated documentation for the Decompresss action to reflect new recursive parameters
- Fix: deleting by timed delete policy when content is not present should respect batch limits
- Turn off cache-control response headers from auth
- Fix filtering by flow type on errors page
- Fix search by error cause on the errors page
- Fixed a path in the Decompress action that might not use the correct content name for a decompress
- Fix queries for autoresume and cold queuing that were not properly fetching the entire deltaFile
- Error DeltaFiles when next action configuration is not found for the next pending action to prevent getting stuck in flight
- [deltafi-core] Fix bug in Java plugin generation due to re-name and re-package of RestPostEgress
- Fix type definition bug in 'plugin-init' command
- Fix requeue
- Bug fixed where DeltaFile with version 0 could not be resumed
- When rebuilding flows only carry the running flow state forward to prevent getting flows stuck with an invalid state
- Prevent duplicate plugins by adjusting database primary key
- Fixed rendering issue with New Data Sink dialog on Data Sinks page
- Fixed the query used to update the system plugin version in flows
- Fixed the query used to search for running flows by plugin coordinates
- Reset test mode and max errors on flows when running a hard snapshot reset
- Fix the query used to find flows by name and type
- Add flyway baseline parameters to migrate existing schema on upgrade
- KinD: Missing `_is_arm()` function restored
- Fix a bug where the wrong JoinEntry can be returned resulting in a duplicate key exception
- Support optional maxNum join settings in the StateMachine
- MaxErrors should be aggregated by data source names, not flow names
- Populate ancestor flow numbers
- Fix issue where revalidation of plugin currently being registered could cause a rollback exception preventing registration
- Add default timestamp to analytic event
- Immediately update a DeltaFile's status in the database when it is resumed. This fixes cases where it would not show up as resumed while actions were still being performed and cached locally.
- Fixed bug preventing snapshots from being imported
- Track DeltaFiles that fail to update during timed join processing and retry them later to prevent them from becoming stuck in flight
- Fixed a bug where uninstalling a plugin while running in compose could return the incorrect container id to remove due to a regex name filter
- When using the "Save and Run Now" option for resume policies, roll the `nextAutoResume` time up to the DeltaFileFlow
- Fix dataSource sorting and other camelCase fields when using the graphQL deltaFiles endpoint's orderBy
- Fix bug where immutable childDids collection was returned from the database

### Removed
- MongoDB removed, replaced with PostgreSQL
- DeltaFi API v1 is no longer available
- Removed deprecated survey rest endpoint
- Removed deprecated survey Grafana dashboard
- Python test kit: Removed old action types, Domain, Enrich, and Validate
- Removed CLI command `integration-test`
- Removed `data-source` command
- Removed the standalone `deltafi-auth` and `deltafi-monitor` deployments
- Removed deltafi-ui and deltafi-docs containers
- Removed Transform Builder from sidebar menu
- Clickhouse removed for all orchestration systems
- Grafana: Clickhouse diagnostic dashboards removed
- Grafana: Clickhouse analytics removed
- Removed `deltafi.ssl.*` fields from the `values.yaml` leaving just the secret field. Core and plugins are now setup to look for key and cert files in a fixed location (/certs)
- Removed the plugin image repository related code
- Remove ingress and delete audit logging
- Remove unused flow plan coordinates from DeltaFileFlow
- Remove destination from EgressResult. Metrics are already associated with the egress.
- Remove separate ingress deployment, have core workers handle ingress and immediately affinitize and cache DeltaFiles
- Remove option to turn off the DeltaFileCache
- Metrics: Action level byte and file counts removed
- Removed vestigial deltafi-auth code from the core codebase

### Security
- Made SSL ciphers configurable

### Tech-Debt/Refactor
- Replaced deprecated CountingInputStream with BoundedInputStream in all usages
- Moved building ActionDescriptor from Action to PluginRegistrar
- Removed contentStorageService from Action
- Moved authentication from the standalone `deltafi-auth` project into `deltafi-core`
- Improve the locking mechanism for the DidMutexService. Use a global ReentrantLock instead of relying on a local synchronized method.
- Identify flow referenced by action event by id only, not id and name
- Ensure DeltaFiles are fetched in a single database transaction.
- Add flyway migration baseline
- Store actions as jsonb in the deltaFile flows table
- Only run the JoinEntryLockCheckScheduler on the core with schedule.maintenance set
- Optimize ErrorCountService
- Remove deprecated noverify startup flag from core
- Improve auto-resume performance
- Use pem files for setting up SSLContexts instead of JKS or p12 formats
- Made all fields in ActionParameter subclasses private
- Remove the flowPlans postgres table. Begin simplifying plugin code.
- Relocated core.exception classes to deltafi-core source tree (InvalidActionEventException UnexpectedActionException UnexpectedFlowException)
- Remove explicit transaction management from insertOne, as it is already handled by the @Transactional annotation
- Remove redundant @Index annotations, since these are now handled by Flyway
- Speed up single DeltaFile inserts
- Remove unused normalizedName field from DeltaFile
- remove unused action event queue methods
- Tweak parameters to silence startup warnings and speed up startup
- Standardize action parameter class annotations
- Optimize subscriber lookups
- Do not reload entire flow cache on timed data source state changes
- Remove unused graphql fields
- improve requeue query performance
- improve data source created before delete policy performance
- remove temp table when marking content deleted
- Clean up warnings and dead code paths throughout the codebase
- Change delta_files and delta_file_flows postgres jsonb columns to arrays where possible
- Change postgres toast compression algorithm from pglz to lz4 for quicker jsonb access
- Squash flyway migrations
- Reduce storage size of tables in postgres. Reorder fields for byte alignment, use postgres enums, exclude nulls and blanks in jsonb, and abbreviate jsonb field names.
- Improve performance of transforms and dataSinks filters

### Upgrade and Migration
- Updated Java dependencies
  - nifi-flowfile-packager 1.27.0
  - bcpkix-jdk18on 1.78.1
  - json-schema-validator 1.5.2
  - commons-text 1.12.0
  - dropwizard 4.2.28
  - kubernetes-client 6.13.4
  - docker-java 3.4.0
  - postgresql 42.7.4
  - hypersistence-utils-hibernate-63 3.8.3
  - flyway 10.20.0
  - nifi 1.27.0
  - dgsCodegen 6.3.0
  - dgs 9.1.3
  - jackson 2.17.2
  - json schema generator 4.36.0
  - minio 8.5.12
  - spring boot 3.3.4
- New `Integration Tests` permissions/roles: IntegrationTestLaunch, IntegrationTestView, IntegrationTestDelete
- Upgraded python base image to 3.12.4-0
- Updated python dependencies versions for: minio, pydantic, pytest, redis, requests, and urllib3
- Added Python 3.12 to the CI deltafi-build image
- Python module deepdiff now requires installation of numpy
- New table `integration_tests` and column name change in `test_results`
- Integration test YAML now requires `name` field
- Update clustermonitor kubectl to latest version (1.31.2)
- This clears the DeltaFiles, DeltaFileFlows, and Annotation tables. Minio data should be manually deleted.
- Upgrade KinD to 1.31.0
- Plugins must be recompiled with the latest action kit
- KinD: due to orchestration file relocation, execution of `orchestration/kind/install.sh` will be
  necessary to re-configure tooling
- TransformResults now collect a new type, `ChildTransformResult`, in both java and python instead of collecting objects of type TransformResult.
Java Sample:
```java
TransformResults manyResults = new TransformResults(context);
ChildTransformResult result = new ChildTransformResult(context, name);
manyResults.add(result);
```
Python Sample:
```python
transform_many_result = TransformResults(context)
child = ChildTransformResult(context, name)
transform_many_result.add_result(child)
```
- Upgraded GraphiQL UI to 3.4.0
- Upgrade to Valkey 8.0.1
- Upgrade to Java 21.0.5
- Updated python packages to match deltafi/python:3.12.7-0 image:
    - minio: 7.2.10
    - PyYAML: 6.0.2 (new)
    - pydantic: 2.9.2
    - redis: 5.2.0
    - urllib3: 2.2.3
    - pytest: 8.3.3
- Seamless upgrade from release candidates is not possible since Flyway will remember the old migrations. In postgres run `delete from flyway_schema_history;` before upgrading from an RC to 2.0.0.

## [1.2.20] - 2024-09-27

### Added
- Added 7Z support to decompress actions

## [1.2.19] - 2024-09-09

- No changes for this release

## [1.2.18] - 2024-09-09

### Added
- Compose - added additional health checks for Loki and Grafana

### Changed
- Increased HTTP body size for compose NGINX

## [1.2.17] - 2024-09-03

### Added
- Nodemonitor will automatically create application metrics when docker is available.  This will
  allow application metrics in the compose configuration when clustermonitor is not available.

### Changed
- Renamed chart header on dashboard from "Delete Policy Activity" to "Content Removed"

### Fixed
- Added DataKey to all DataTables.
- RecursiveDecompress action now  batches saves to MinIO
- Lock down Python dependency version to match those found in deltafi/python:3.12.1-1
- Fixed potential bug related to default SSL ciphers used in Kubernetes ingress.

## [1.2.16] - 2024-08-20

### Changed
- Grafana is now accessible via `/visualization`
- Embedded charts in the UI now use `/visualization` instead of subdomain
- Grafana is now accessible via `/visualization` in compose mode.

### Fixed
- Fixed auth domain permissions for compose.
- Fixed the "double authentication" issue when using basic auth

### Security
- Made SSL ciphers configurable

## [1.2.15] - 2024-08-12

### Added
- Added Helper buttons calender refresh to Events page
- New RecursiveDecompress transform action, which recursively decompresses content, drops compression file suffixes, and catalogs file lineage

### Changed
- Egress sink runs on port 80 instead of 9292
- Airgap no longer needs to override defaults for passthrough

### Fixed
- Fixed issue in search page calendar looking for data not available on prod builds
- Arrays of integers are no longer being removed from flows on save.

### Upgrade and Migration
- Grafana -> 11.1.3
- Java -> 21.0.4
- Ruby -> 3.3.4

## [1.2.14] - 2024-08-01

### Added
- Added deltafi/docker-web-ui container to the stack
- Compose stack: compose script has `logs`, `ps`, `top`, `pause`, and `unpause` commands

### Changed
- Compose stack: Remove mongo DB on uninstall, instead of dropping collections
- Compose stack: Compose script will remove mongo DB on uninstall
- Compose stack: Compose script will clean up plugin containers on uninstall

### Fixed
- [deltafi-python] Fix python 3.12 thread compatibility that was affecting the MinIO client (GitLab-300)
- Compose stack: Removed dependency on Ruby to run CLI

## [1.2.13] - 2024-07-22

### Added
- Limit to 31 days of Loki logs on compose stack
- Compose install can disable clickhouse with values.yaml config
- Airgap install uses an airgap specific values.yaml for additional configuration
- Airgap includes yq and jq dockerized tool and execution scripts
- Airgap includes lazydocker for on box docker diagnostics and monitoring without UI
- Added mandatory static list of docker images to airgap docker manifest generator

### Fixed
- Search Page Calendar Helper now base times off of current time not the time when the calendar dialog was opened.
- Search Page Calendar now shows the time being used every time the dialog is reopened instead of whatever values it was previously closed with.
- Values selected in the dropdowns in the JSON Renderers can now be cleared out
- Improved clickhouse CPU/RAM performance on compose stack
- Disable redis persistence on compose stack
- Compose: Timeouts on startup fixed for slower VMs
- Compose: Changed docker user execution to local user for most pods that mount local volumes to resolve permission issues on some Linux configurations
- Fixed an issue with the monitor healthcheck in compose environments.

## [1.2.12] - 2024-07-12

### Added
- CLI: list-ingress-actions command added
- CLI: ingress-action command added
- compose script now has a 'build' target that will build all core docker images.  Useful for running compose with snapshots
- Autodetect Linux and MacOS in compose script
- Added destroy command to compose script
- Healthcheck for plugins installed in compose, the installation is not considered complete until the container reaches a healthy state
- Added rollback logic for failed plugin installs in compose
- Added restart policies for compose services

### Changed
- CLI: Modified list-flows to list timedIngress flows
- Compose script now creates data directories with the correct permissions

### Fixed
- Fixed bug that prevented plugins from being upgraded when running in compose
- CLI: `deltafi install` will return an error when it fails

### Upgrade and Migration
- Use bitnami/redis:7.2.5 in compose stack and kubernetes
- Upgrade loki to 2.9.9
- Upgrade promtail to 2.9.9
- Upgrade minio to RELEASE.2024-07-04T14-25-45Z
- Upgrade clickhouse to 24.6.2-debian-12-r0

## [1.2.11] - 2024-07-02

### Added
- Installer Builder for standalone DeltaFi systems targeting airgapped networks (compose/airgap/airgap-inator)

### Fixed
- Fixed bug in Additional Properties Renderer where "[object Object]" appears when a new property is added

## [1.2.10] - 2024-06-24

### Added
- Added clickhouse to compose

### Changed
- Update the monitor k8s resource check to only execute when it is running in a cluster

### Upgrade and Migration
- Updated to the following images in compose:
    - grafana: deltafi/grafana:11.0.0-0
    - graphite: graphiteapp/graphite-statsd:1.1.10-5
    - loki: grafana/loki:2.9.3
    - minio: quay.io/minio/minio:RELEASE.2024-06-11T03-13-30Z
    - nginx: docker.io/nginx:1.27.0-alpine
    - promtail: docker.io/grafana/promtail:2.9.3
    - mongodb: docker.io/bitnami/mongodb:5.0.17
    - redis: docker.io/bitnami/redis:7.2.4
    - clickhouse: bitnami/clickhouse:24.3.3-debian-12-r2

## [1.2.9] - 2024-06-14

### Changed
- Auto Resume remove confirmation should show Name instead of Id

### Fixed
- Fixed errors displayed when installing a plugin on the Plugins page
- Trim groupId, artifactId, and version on Plugins page
- Sorted plugin list in "Select a Plugin" dropdown on Flows page
- Fixed default boolean and integer values not being set in Actions in Flow Builder

### Upgrade and Migration
- Minio: RELEASE.2024-06-11T03-13-30Z
- Upgrade to Grafana 11.0.0
- Upgrade to Clickhouse 24.3.3

## [1.2.8] - 2024-05-24

### Fixed
- Back-port 2.0 Python Test Kit fixes into 1.2:
  - Fix passthrough data checks
  - Bug fix for JsonCompareHelper and enable regex support
- Resolved an issue causing a Internal Server Error (500) during authentication attempts by users lacking a password.

## [1.2.7] - 2024-05-09

### Added
- Added a metadata trigger to ErrorByFiatTransformAction
- `ClickhouseService` in core
- `Error Analytics` dashboard added to Grafana
- New Clickhouse table captures all occurrences of errors with flow, error cause and annotations

### Changed
- MongoDB runs in kubernetes as a stateful set
- Grafana runs in kubernetes as a stateful set

### Fixed
- Fixed Boolean values being removed on Flow Builder saves
- Removed asterisks from two non-mandatory fields on the Auto Resume Rule form.

## [1.2.6] - 2024-04-25

### Changed
- Documentation for initial install to include enabling tls in kubernetes

### Upgrade and Migration
- Upgrade to deltafi/grafana:10.4.2-0
- Upgrade Java base image to deltafi/deltafi-java-jre:21.0.3-alpine-0
- Upgrade CI build image to use Java 21.0.3
- Upgrade Ruby base image to deltafi/deltafi-ruby:3.3.1-alpine3.19-0

## [1.2.5] - 2024-04-16

### Added
- Documentation for DeltaFile analytics and new survey
- Additional logging on Egress action errors

### Changed
- Changed the requeueSeconds property to requeueDuration
- Changed the deltaFileCache.syncSeconds property to deltaFileCache.syncDuration

### Fixed
- Potential thread interrupt deadlock in HTTP egress actions
- Potential thread interrupt deadlock in ActionRunner
- error in `migrate-to-durations.js` migration

### Removed
- Deprecated survey API documentation

### Deprecated
- Deprecation of original `/survey` rest endpoint in favor of the more robust `/api/v1/survey` API.  The original rest endpoint will be removed in 2.0.
- `/survey` REST endpoint has been deprecated and will be removed in DeltaFi 2.0.  The `/api/v1/survey` API should be used for annotation of survey related data.

### Tech-Debt/Refactor
- Added DeltaFile schema version tests for v6 and v7
- Improved UI test reliability and speed.

### Upgrade and Migration
- The migrate-to-durations.js script (automatically run during installation) updates the deltaFiProperties and
systemSnapshot Mongo database collections with the following:
  - Replaces requeueSeconds with requeueDuration of "PT{requeueSeconds}S"
  - Replaces deltaFileCache.syncSeconds with deltaFileCache.syncDuration of "PT{deltaFileCache.syncSeconds}S"
  - Replaces `REQUEUE_SECONDS` with `REQUEUE_DURATION` in the setProperties array
  - Replaces `DELTAFILE_CACHE_SYNC_SECONDS` with `DELTA_FILE_CACHE_SYNC_DURATION` in the setProperties array
  - Replaces `DELTA_FILE_CACHE_SYNC_SECONDS` with `DELTA_FILE_CACHE_SYNC_DURATION` in the setProperties array
- Redis 7.2.4
- Minio RELEASE.2024-04-06T05-26-02Z
- Clickhouse 24.3.2-debian-12-r2

## [1.2.4] - 2024-04-09

### Added
- Added the ability to add Publish Rules to Ingress Actions on the Ingress Actions Page
- Add Subscriptions to Transform Actions in the Flow Plan Builder Page
- Presort "By Flow" and "By Message" tabs on Errors Page by flow name
- Added a Resource.read method that takes a Charset for string decoding
- Default values for action parameters are now set on the action edit page in the flow builder.
- Grafana: Ability to create moving averages and linear/polynomial regression on dashboards
- Grafana: Globally tagged annotations will appear on every dashboard chart
- Grafana: System Overview chart now has `Global Events` panel to display annotation details
- Added a new cron schedule UI for Timed Ingress Actions.

### Changed
- Moved `utils` directory to `bin`.  Left a symlink to utils for backward compatability.
- Build a virtual environment for `deltafi-python` build to insure broad build compatability
- Added CI retries on UI eslint and test jobs

### Fixed
- cluster command halts if gradlew fails
- Do not copy the old cron schedule or target flow when saving a TimedIngressFlow
- Clustermonitor: Broken app level performance metrics (regression in upgrades)
- Bad change for testing purposes resulted in malicious MongoDB delete queries

### Upgrade and Migration
- Update nginx and kubectl base images
- Move clustermonitor base image to latest Ubuntu LTS
- Grafana upgrade to 10.4.1
- Upgrade to DGS 8.4.4
- Upgrade to Spring Boot 3.2.4
- Upgrade Palantir Docker plugin to 0.36.0
- Upgrade Jackson to 2.16.2
- Upgrade OpenFeign to 13.2.1
- Upgrade dependencies:
  - commons-compress: 1.26.1
  - commons-io: 2.16.0
  - httpclient: 5.3.1
  - jsch: 0.2.17
  - json: 20240303
  - JSONSchemaGenerator: 4.35.0
  - json-path: 2.9.0
  - json-schema-validator: 1.1.0
  - kubernetes-client: 6.10.0
  - logback-classic: 1.5.3
  - lombok: 1.18.32
  - dropwizard metrics-core: 4.2.25
  - minio-client: 8.5.9
  - nifi-flowfile-packager: 1.25.0
  - tika-core: 2.9.1
- Upgrade test dependencies:
  - assertj: 3.25.3
  - jupiter: 5.10.2
  - rest-assured: 5.4.0
  - testContainers: 1.19.7
  - wiremock-standalone: 3.5.2
- Update CI build image to JDK 21.0.2
- Update base image for Java applications to deltafi/deltafi-java-jre:21.0.2-alpine-0
- KinD image upgrade to support KinD 0.22.0
- Upgrade Python dependencies to match 3.12.1 base image
- Upgrade to ruby 3.3.0 for all Ruby base images
- Moved UI base image to Node 20 and Alpine 3.19

## [1.2.2] - 2024-03-22

### Added
- UI: Added overall and by-node charts to System Metrics page.
- Added external link indicators to sidebar menu.

### Fixed
- Fixed panel header height on Ingress Routing page.
- Fixed a bug when viewing content containing multiple files of different file types.
- Fixed the handling of carriage returns in the BoundedLineReader

## [1.2.1] - 2024-02-23

### Added
- Added the ability to add and update Ingress Actions to UI.
- Added scrollToTop on the actionConfigurationDialog when there are errors
- UI: Clicking on an action in a `RETRIED` state on the DeltaFile Viewer now shows the error cause and context.
- UI: Display local git branch in UI when running in development mode.

### Changed
- Consolidated metrics chart panels on the dashboard into a single Metrics panel and incorporated a timeframe dropdown.
- Changed default chart refresh interval from 5 minutes to 1 minute.
- Description is now an editable field for existing flows in the flow plan builder
- UI: Content Viewer now detects JSON and XML content regardless of `mediaType`.
- Replaced "Show Acknowledged" button on Errors page with a dropdown of Errors, Acknowledged, and All.

### Fixed
- Fixed bugs with Flow Plan Builder collect fields
- Fixed headers on all dialogs and in the Flow Plan Builder
- Fixed issue when you have a property that uses the boolean json schema renderer the initial value put into the renderer is held on even if you try to change it.
- Fixed issue where our Integer Json Renderer wasnt able to support string forms of numbers.
- Fixed a bug on the search page that was causing the back button to not behave as expected.

### Tech-Debt/Refactor
- Moved Calendar into a component.

### Upgrade and Migration
- Upgrade clustermonitor to kubectl 1.29.1
- Update nodemonitor to alpine:3.19.1 base image

## [1.2.0] - 2024-01-27

### Added
- Added automatic Java 21 toolchain selection using `jenv`
- Added `resolveDeps` tasks to gradle subprojects

### Changed
- Java toolchain is now 21.x

### Tech-Debt/Refactor
- Added parallel JUnit test execution to improve build performance when running unit tests
- ByteBuddy dependency is running in "experimental" mode for Java 21 compatibility

### Upgrade and Migration
- Upgrade Gradle to 8.5
- Migrate core projects to use Java 21
    NOTE: Plugins running against the new Java action kit must be recompiled with Java 21 toolchain
- Update CI build image to JDK 21.0.1
- Update base image for Java applications to deltafi/deltafi-java-jre:21.0.1-alpine-0

## [1.1.20] - 2024-01-26

### Changed
- Switched from version-reckoning to git-version plugin for generating the Gradle application version based on git tags

### Upgrade and Migration
- UI library update
- git-version plugin 2.0.1 that is fully compatible with Java 17 and 21
- Upgraded KinD to run on Kubernetes 1.29.0 by default

## [1.1.18] - 2024-01-13

### Changed
- UI: Rearranged sidebar menu by moving DeltaFiles above Metrics for improved navigation.
- `cluster destroy` command will optionally destroy all docker registry containers and volumes associated with the cluster

### Fixed
- Addressed the following missing features in the python test kit:
  - added DOMAIN, ENRICH, EGRESS, and VALIDATE actions
  - added support to check metrics
  - allow DID to be specified by test case
  - Updated Error and Filter result processing to optionally check `context` and `annotations`
  - Fixed bug when checking for a Filter result

### Removed
- UI: Action Metrics page
- UI: Action Metrics from Flows page

## [1.1.17] - 2024-01-08

### Added
- Added Subscriptions to Transform Flow view
- Added Transform Flow Mocks
- Added a SftpTimedIngressAction class to poll a STFP server for files to ingest.

### Changed
- Redis no longer uses zcube for arm64 images, since bitnami/redis is now published with an arm64 image

### Fixed
- During startup, failures by core to query api get a small grace period before generating an error, and repeated errors for the same error are no longer logged continuously

### Tech-Debt/Refactor
- Updated MergeContentFormatAction to use WriterPipedInputStream and added missing unit tests.

### Upgrade and Migration
- Subproject upgrades
  - redis (bitnami/redis:7.0.14)
  - promtail (bitnami/promtail:2.9.3)
  - loki (bitnami/loki:2.9.3)
  - clickhouse (bitnami/clickhouse:23.12.1-debian-11-r0)
  - minio (minio/minio:2024-01-01T16-36-33Z)

## [1.1.16] - 2023-12-15

### Added
- Added `--quiet` option to mongo-eval CLI
- Added topics where `DeltaFiles` can be published to and subscribed from
- Added the option to put publisher rules in `TimedIngressFlows` that control where DeltaFiles are sent
- Added the option to put subscriber rules in `TransformFlows` that allow the flow to pick up `DeltaFiles` from one or more topics
- Added a mutation to task a `TimedIngressFlow` on demand with the option to override the `memo` value

### Changed
-Add better indication of required field throughout web UI.

### Fixed
- Fixed a bug when cloning a flow from the flows page the "Clone From: (Optional)" not populated correctly.
- Regression in content API endpoint causing issues with missing content.
- Fixed collected DeltaFiles not completing when aggregate stage changes.
- Updated the `uninstall` CLI command to properly parse the latest `mongo-eval` output format when attempting to drop collections
- Spurious Grafana restarts on helm installs

### Security
- Eliminated the long-standing snakeyaml 1.33 dependency, clearing out all CVEs in core Java

### Upgrade and Migration
- Grafana helm chart upgrade to 7.0.17
- Ruby base image and dependency updates
- DGS upgrade to 8.2.0 (from 7.6.0)
- Spring Boot upgrade to 3.2.0 (from 3.1.6)

## [1.1.15] - 2023-12-11

### Added
- ErrorResult and FilterResult now allow annotations

### Fixed
- Clickhouse enable was not complete in the new helm chart, which resulted in a clickhouse pod being started (and other k8s artifacts) even when `clickhouse.enable` set to `false`

### Upgrade and Migration
- Java base image upgrade: deltafi-java-jre:17.0.9-0 (Java 17.0.9+9)

## [1.1.14] - 2023-12-10

### Added
- Show `filteredContext` in UI for Actions in a `FILTERED` state.
- Allow separation of general and detailed DeltaFile filtering reasons by adding optional (nullable) `filteredContext`to FilterResult
- New `namespace` and `node` tags for `gauge.app.memory` and `gauge.app.cpu` metrics
- New metric: `gauge.node.cpu.iowait` to track iowait pressure on nodes
- New dashboard: `DeltaFi > System Performance`
- Improved Server Sent Event error handling in the UI.
- CLI: Added `clear-memo` and `set-memo` options to timed-ingress commands
- New mutation `setTimedIngressMemo` allows the `memo` of a TimedIngressAction to be set (or cleared if `null`). The TimedIngress must not be RUNNING. Requires the ADMIN role

### Changed
- Improved error message and cause filtering on Errors and Filtered pages.
- Improved `Clickhouse flows by annotation` dashboard with improved flow detail and summaries
- System Performance dashboard has gauges for CPU/RAM on each node
- System Overview has similar layout to the Flow By Annotation dashboard
- Flow By Annotation tables can be filtered and sorted
- CLI: mongodb commands updated to use mongosh and new environment variables

### Fixed
- Use the `created_before_index` for delete policies that are based on the created date
- DeltaFile stats metrics were not publishing
- Extraneous warning in clickhouse helm chart
- Disk usage bar chart fixed on System Overview
- Fixed naming of Flow By Annotation tables
- Removed deprecation warnings from mongo migrations
- KinD: Ingress NGINX initialization failure fixed by upgrading to newer ingress-nginx/controller image
- Fixed echo message in timed-ingress CLI
- Fixed GraphQL field name `filteredContext` in Action type to match Java
- Fixed support for FlowFile streaming (v2 and v3). A single FlowFile may result in multiple FlowFiles ingressed.
- Resolved a bug in the API that caused Server Sent Events to be initially sent with null values.

### Removed
- System Overview dashboard: Removed app CPU/RAM charts.  These charts were moved to the `System Performance` dashboard

### Tech-Debt/Refactor
- Query current action queue sizes from redis instead of graphite
- Move error acknowledged from partial index criteria to index field in mongo error index
- Batched metrics in nodemonitor and clustermonitor to improve graphite performance
- Improve performance of the deltafi-monitor
- Refactored collect (primarily by removing the READY_TO_COLLECT action state) to stop additional Mongo writes
- Reduce API memory footprint and CPU usage
- Decrease API response times for metrics/content requests
- Reduce delay before seeing initial status report when the UI is first loaded
- Change default number of API workers from 8 to 4
- API: Reduce memory usage and latency for content endpoint
- API: Reduce memory allocation in survey endpoint
- Improve rebuild velocity for deltafi-api docker images

### Upgrade and Migration
- Dependency upgrades:
  - minio: quay.io/minio/minio:RELEASE.2023-12-02T10-51-33Z
  - promtail: grafana/promtail:2.9.2
  - loki: granfana/loki:2.9.2
  - registry: deltafi/registry:2.8.3-0
  - clickhouse: bitnami/clickhouse:23.10.5-debian-11-r0
- KinD: k8s.gcr.io/ingress-nginx/controller:v1.2.1
- New versions of dependent subsystems:
  - deltafi/grafana:10.2.2-0 (Grafana 10.2.2)
- Java dependency updates, including:
  - DGS: 7.5.3 -> 7.6.0
  - Jackson: 2.15.2 -> 2.16.0
  - Spring Boot: 3.0.11 -> 3.1.5
- Helm chart upgrades
  - minio - 5.0.9
  - mongodb - 14.4.0
  - redis - 18.4.0
  - kubernetes-dashboard - 6.0.8
  - promtail - 6.15.2
  - loki - 2.16.0
  - grafana - 7.0.11
  - clickhouse - 4.1.8
- When installing this version, mongodb will be updated and due to a bug in mongodb rolling initialization, the mongodb pod in a kubernetes cluster should be scaled to 0 instances:
```bash
# Prior to upgrade, scale the deltafi-mongodb pod to 0
kubectl scale deploy deltafi-mongodb --replicas=0
# During upgrade, after helm starts, scale the deltafi-mongodb pod to 1
kubectl scale deploy deltafi-mongodb --replicas=1
```

## [1.1.13] - 2023-11-20

### Changed
- A DeltaFile that has been scheduled for an auto-resume attempt can now be cancelled
- Restore "created before" mongo index
- Rewrite egress sink in go

### Fixed
- Fixed Json Schema configuring maps issues
- Combine flow and action name when queuing collect actions.

### Tech-Debt/Refactor
- Standardize stylings on all JSON-form renderers used in flow builder
- Auth, API, Clickhouse ETL, and Monitor: Replaced standard JSON library with Oj optimized JSON library

### Upgrade and Migration
- Moved API and Auth to new ruby base image: deltafi/deltafi-ruby:3.2.2-alpine3.18-1

## [1.1.12] - 2023-11-19

### Added
- Added ability to run Auto Resume rules manually from the UI.
- Add inFlight, terminal, and contentDeleted properties to DeltaFiles for more efficient indexing
- Added collect support in Flow Plan Builder.
- Added validation to flow builder to prevent saving if the flow won't be accepted.
- Added visual indicators to panels to alert if there is a required action not provided.
- Added overlay dismissal of the new action popup tree when the maximum number of actions added has been reached.
- Added raw json view to view flow plan json and schema. Dialog is triggered by pressing "d+e+v".
- Add ingressBytes index
- Add log message when timed delete starts, mirroring existing disk space delete message
- New CLI command `timed-ingress` to start/stop a timed ingress

### Changed
- Use a lower cost bcrypt hash for passwords in basic auth to improve performance
- Disk space delete policies sort by modified date instead of created date
- Flush DeltaFileCache to disk on core shutdown
- Ensure the old core pod is stopped before starting the new one during an upgrade

### Fixed
- ETL was syncing to the most recent deltafile in the clickhouse database for the timestamp for beginning Mongo deltafile queries.  In the case where surveys were added while the ETL job was down, it woould catch up from the latest survey timestamp instead of the latest regular deltafile timestamp.
- Fix logic error in delete rerun loop
- Increment DeltaFile mongo object version on all updates
- Only add the `filtered` flag to the search criteria once when `filteredCause` criteria is included
- Ensure sort is by modified time when running a complete-time delete policy, sorting correctly and using the index more efficiently
- Fixed flow builder configuration pop up saving selected "Clone From" data when "Type" is changed or removed.
- Refresh the resume policy cache before attempting to apply policies in case they were just updated by another core pod
- Fix a null pointer exception when building ActionInput for timed ingress actions

### Tech-Debt/Refactor
- Added missing UI mocks for __Filtered__ and __Ingress Actions__ pages.
- Cluster monitor sends metrics to graphite in one batch
- When possible, only update fields that have changed when updating DeltaFiles
- Optimize indexes to improve performance of Mongo deltafile delete queries
- Reduce number of mongo queries from clickhouse ETL job
- Cleaned up HoverSaveButton.vue file that the Flow Builder uses.
- Reduce FlowfileEgressAction memory usage by streaming data
- Reduce Flowfile Ingress memory usage by streaming data
- set Minio scanner speed to slow to prioritize reads and writes over lifecycle events
- turn Minio browser off
- Performance improvements for AUDIT logging
- Pydantic Python API/interface updated to v2
- Queue management service was querying the database for action types, use cached data instead

### Upgrade and Migration
- If using basic auth, resave your password on the Users screen so that it is stored at the new cost setting.
- Ensure ingress is disabled and all files are at rest before beginning upgrade, as the migration is slow and must update all DeltaFiles
- Minio RELEASE.2023-11-15T20-43-25Z
- Python plugins must be updated to use the Python Pydantic v2 API/interface

## [1.1.11] - 2023-11-08

### Changed
- Configured `egress-sink` to use rainbows/unicorn for better performance.
- Change default `scheduledServiceThreads` from 32 to 8
- Change default `deltaFileCache.enabled` to true
- UI: Changed width of Cron Schedule column in Timed Ingress Actions table.
- Change default `requeueSeconds` from 30 to 300 and `deltaFileCache.syncSeconds` from 10 to 30 to reduce mongo strain and prevent races between the cache and requeue

### Fixed
- Resolved a bug that was causing slow selection of DeltaFiles on the Search page.
- Allow strings up to 16 MB to be serialized into ActionInputs in the Java action kit
- Ensure reinjected child actions are cold queued if needed
- Improve core memory use when too many files are flowing through the system. Introduce a semaphore to limit how many messages will be pulled off the queue, configurable with the `coreInternalQueueSize` property.
- Do not start unnecessary processing threads in ingress.
- UI: Timestamp component now renders "-" instead of "Invalid Date" if passed a null/undefined.
- Fixed bug in egress-sink causing no files to be written to disk.
- Include `SCHEMA_VERSION` field in all partial DeltaFile queries so that up conversion is not attempted.
- Fix memory issue: Replace Metric tag string concatenation with StringBuilder

### Tech-Debt/Refactor
- Do not create empty arrays and maps when initializing DeltaFile and Action objects, since they will be overwritten upon serialization
- Refactor DeltaFileImpl mongo queries using fluent interfaces.  Creates cleaner code and queries sent to the DB.
- Reduce minio partSize from 100,000,000 to 10,000,000 to reduce memory allocations

## [1.1.10] - 2023-11-06

### Fixed
- Avoid extra calls to minio delete if content has already been deleted.

### Removed
- Clickhouse related INFO logging on every write (ETL and API)
- Core logger sequence numbers

### Tech-Debt/Refactor
- Have disk space delete policy use cached values for content storage metrics, avoiding repeated calls that can bog down the API
- Cleaned up rubocop warnings in deltafi-api
- Refactored clickhouse client to its own module
- When mongo or the core are busy for long periods of time due to bursts of traffic, timed delete policies can fall behind. Ensure that disk delete policies rerun in between batches of timed deletes.
- Move the `DeltaFiPropertiesService` initialization from a @PostConstruct method to the constructor.
- Remove stray DeltaFilesService @MongoRetryable annotations. OptimisticLockingFailureExceptions are handled in one place in processResult().
- Spread processing of requeued DeltaFiles to all core workers instead of just the core.

## [1.1.9] - 2023-11-03

### Added
- Implemented page routing guard that can be used to prevent navigating to another page if certain conditions are met.
- Added validation of timed ingress cron schedule.

### Changed
- Added collect configuration in flows.
- Removed JOIN action type from Auto Resume page.
- Updated Headings on errors page tabs.
- Change helm chart `nodemonitor` name to `deltafi-nodemonitor` for consistency with other apps
- Replaced interval with cron expression in timed ingress
- Update documentation to reflect supported OS/configurations, consolidate pre-reqs/usage of KinD environment, and bring pages for KinD usage and Contributors to the top-level.
- Changed default value for `smokeEgressUrl` to `http://deltafi-egress-sink-service/blackhole`.
- Changed interval to cron schedule in timed ingress docs.

### Fixed
- Fixed flow plan builder redirect catch always firing on save while editing an existing flow plan
- Fixed a bug that prevented the API from rendering content for filenames containing commas.
- Prevent memory growth in core-actions by saving non-streaming archives to minio in batches
- UI: Resolved a CSS bug that was impacting the menu in the content viewer.
- Core should heartbeat queue names, not identity.  Queue name is prepended with dgs-.  These were showing up as orphaned queues.
- Allocate two additional threads to the core jedis thread pool to account for the incoming action event threads
- Core and workers emit heartbeat for the non-affinitized dgs topic so it is not reported as an orphan
- Fix race where deltaFile cache cleanup could remove a deltaFile in process from the cache
- Mongo excessive CPU usage caused by @ConditionalOnProperty annotation being ignored when used in conjunction with @Scheduled in QueueManagementService

### Removed
- Removed processing type from GUI search options
- Removed smoke file creation from egress-sync in favor of `smoke-test-ingress` timed ingress action.

### Tech-Debt/Refactor
- Restructure deltaFileStats to reduce mongo CPU load.  Total bytes is no longer displayed on the top bar of the dashboard.
- Improve DeltaFile cache flushing.  Flush to the database at the configured deltaFileCache.syncSeconds interval.  Previously it was only flushing if the modified time was greater than this interval, but now it checks against the last database sync time.  This fixes a race with requeues that would often cause Mongo OptimisticLockingExceptions and duplicated work.
- Adjusted deltaFile collection indexes for better performance
- Small DeltaFilesService optimizations and cleanups
- Change ConfigurableFixedDelayTrigger semantics so that there is a fixed delay between executions, as opposed to running at a fixed rate
- Optimize deltaFileStats query

## [1.1.8] - 2023-10-28

### Added
- Added new Filtered page similar to Errors page.
- Added the ability to import existing flow plans into the flow builder.
- Added the ability to edit existing system plugin flow plans in the flow builder.
- Added Ingress Actions page.
- Added validation to make sure new flow plans being created dont have name that match names of existing flow plans within our system
- New metric `action_execution_time_ms` tracks action execution time in millisecond per action, and includes class name tag
- New Action Execution Time graph on System Overview
- New `MetadataToAnnotationTransformAction` allows metadata to be filtered, and stored as annotations, with optional key modification

### Changed
- Renamed Action Executions to Action Execution Count on System Overview
- EGRESS metrics are now reported before the DeltaFile is marked as COMPLETE
- Timed Ingress Action Improvements
  - Return results synchronously
  - Block execution until the last result is returned, or until the task is lost
  - Provide a memo field in the context and result, so bookmarks/notes can be passed to the next execution
  - Add result option to begin the next execution immediately, without waiting for the usual interval
  - Add result status and statusMessage
  - Respect maxErrors
  - Add documentation
  - Add python implementation

### Fixed
- Removed focus on the last action name rendered when importing from existing or editing existing flow plans
- Fix bug where cold queued egress actions in an transform flow would not be requeued.
- Gradle dependency issue with action-kit test
- Test framework missing Annotation test methods in `ContentResultAssert`

## [1.1.7] - 2023-10-18

### Added
- Added a `system-plugin` where flows and variables can be added and removed without a full plugin install
- Added a mutation, `removePluginVariables`, to remove variables from the system-plugin
- Implemented new Flow Plan Builder
- Added new core action, `DeltaFiEgressAction`, for egressing data directly to another DeltaFi instance.
- Added a query, `filteredSummaryByFlow`, to get a summary of filtered DeltaFiles grouped by flow
  ```graphql
    query {
      filteredSummaryByFlow {
        offset
        count
        totalCount
        countPerFlow {
          flow
          count
          dids
        }
      }
  }
  ```
- Added a query, `filteredSummaryByMessage`, to get a summary of filtered DeltaFiles grouped by flow and the filter cause message
  ```graphql
    query {
      filteredSummaryByMessage {
        offset
        count
        totalCount
        countPerMessage {
          flow
          message
          count
          dids
        }
      }
    }
  ```
- Grafana dashboard 'Clickhouse flow by annotation' that filters on flow and a generic annotation key.  This will allow graphing based on any flow/single annotation key combination
- Support for collecting multiple DeltaFiles for processing by a transform, load, or format action
- Add a check for long-running actions (>5 seconds)
- Foundational work for plugin-delivered timed ingress actions. This is not yet meant for production use.
- New clustermonitor pod that polls kubernetes app metrics and records per-app CPU (in milli-cores) and RAM utilization
- New metrics produced:
  - `gauge.app.memory` tagged by pod container name
  - `gauge.app.cpu` tagged by pod container name
- `System Overview` dashboard updated with the following graphs:
  - `Pod RAM Utilization Over Time`
  - `Pod RAM Utilization` pie chart
  - `Pod CPU Utilization Over Time`
  - `Pod CPU Utilization` pie chart
- New GraphQL query endpoint `resumePolicyDryRun` allows a preview of how many DeltaFile errors might be auto-resumed by a new resume policy
- New user role `ResumePolicyDryRun` in the `Resume Policies` group grants permission to execute the `resumePolicyDryRun` query

### Changed
- Limit flow plan mutations to the system plugin. Attempting to add or remove flow plans to a plugin other than the system plugin will result in an error
- Change the `savePluginVariables` mutation to take a list of variables that is always added to the `system-plugin`
- Cadence update for all libraries minor versions.
- Remove spring-cloud dependency and manually set up kubernetes client
- Update documentation to reflect Ubuntu compatibility and revised system prerequisites (disk space, AVX instruction set
- Reduce default requeue time from 300 to 30 seconds. This was previously raised to mitigate double-queuing issues when an Action took longer than the requeue period to process.
- `cluster`: Plugin configuration simplified to minimally plugin name with an optional git URL

### Fixed
- Fixed bug on the Search page causing Booleans to be parsed incorrectly on page refresh.
- Fixed an error where periodic revalidation of invalid flows persisted missing variable errors that were no longer relevant.
- Clickhouse dashboard 'Clickhouse Metrics' filters correctly on flow for all charts
- Add null check to cold queue logic to protect against bad data and flows that are no longer running
- DeltaFiles in an ERROR stage that have been acknowledged can be deleted by the disk space delete policy.
- Collect entry locking that resulted in multiple collections for the same set of conditions instead of a single collection
- Deprecation issues in `gradle-plugin` with Gradle 8.4
- Do not issue automatic requeues for long-running tasks that are still being processed by an Action
- Remove the `ingressFlowPlan` and `ingressFlow` collections if they are recreated after they have already been migrated to `normalizeFlowPlan` and `normalizeFlow` collections.
- Fixed bug on Flow Plan Builder caused by the addition of `TIMED_INGRESS` actions.
- Bootstrap/install: Issue with using snap to install kubectx on Ubuntu

### Removed
- Grafana dashboard 'Clickhouse flow/subflow' removed

### Tech-Debt/Refactor
- Software license formatter updated
- Refactored nodemonitor configuration in values.yaml to simplify and remove exposure of environment variables.

### Upgrade and Migration
- Upgraded base images for core docker images
- Java plugins using the gradle plugin will be based on deltafi/deltafi-java-jre:17.0.8-0
- Java dependency updates
  - Upgrade caffeine to 3.1.8
  - Upgrade commons-compress to 1.24
  - Upgrade commons-io to 2.14.0
  - Upgrade dgs to 7.5.3
  - Upgrade dgs-codegen to 5.12.4
  - Upgrade dropwizard metrics-core to 4.2.19
  - Upgrade feign to 12.5
  - Upgrade httpclient5 to 3.3.3
  - Upgrade jackson to 2.15.2
  - Upgrade json to 20230618
  - Upgrade json-schema-validator to 1.0.87
  - Upgrade jupiter to 5.10.0
  - Upgrade kubernetes-client to 6.9.0
  - Upgrade logback-classic to 1.4.11
  - Upgrade logstash-logback-encoder to 7.4
  - Upgrade lombok to 1.18.30
  - Upgrade maven-artifact to 3.9.5
  - Upgrade metrics-core to 4.2.20
  - Upgrade minio to 8.5.6
  - Upgrade nifi-flowfile-packager to 1.23.2
  - Upgrade okhttp to 4.11.0
  - Upgrade rest-assured to 5.3.2
  - Upgrade spring boot to 3.0.11
  - Upgrade testcontainers to 1.19.1
  - Upgrade tika-core to 2.9.0
- Gradle version updated to version 8.4 (From previous 7.6).  It is recommended that plugins are upgraded to the same version.
- It is recommended in most circumstances that you set your requeueSeconds System Property to 30 if it was previously set to something higher.
- Updated Python dependencies to match those in deltafi/python:3.10.13-0
  - Python pedantic was upgraded from 1.x to 2.x
    - See migration guide: https://docs.pydantic.dev/2.4/migration/
    - Change imports from `pydantic` to `pydantic.v1` to preserve compatibility
- Subsystem upgrades:
  - Grafana: 10.1.1 (deltafi/grafana:10.1.1-0)
  - Promtail: 2.9.0 (grafana/promtail:2.9.0)
  - Loki: 2.9.0 (grafana/loki:2.9.0)
  - Graphite: 1.1.10-5 (graphiteapp/graphite-statsd:1.1.10-5)
  - Clickhouse: 23.8.2-debian-11-r0 (bitnami/clickhouse:23.8.2-debian-11.r0)

## [1.1.6] - 2023-09-30

### Added
- New `CompressionFormatAction` that compresses/archives all content
- Added save button to Flows Plan Builder page.
- Added remove button to flows page.
- Status check for invalid flows.
- Add addAnnotations GraphQL mutation.
- New action ExtractXmlAnnotationsDomainAction

### Changed
- getAllFlows GraphQL query bypasses caching and always gets live values from the database
- Icons on Flows page are now right-justified.

### Fixed
- Resolve race condition between deltaFile caching and externally applied annotations.
- Error count badge is no longer limited to the first 50000 errors
- System Properties page no longer renders panels for plugins without variables.
- Plugins page now properly handles plugins without variables.

### Removed
- Removed redundant info icon on Flows page.

## [1.1.5] - 2023-09-21

### Added
- Cap size of redis queues. If a redis queue exceeds a configured maximum size, new actions are placed in a "cold queued" state until the pressure has been relieved.
- Added a default connectTimeout of 1 second to the `HttpClient` that is created by autoconfiguration
- Added the `HttpClientCustomizer` interface, plugins can create this bean to customize the settings of the `HttpClient` created by autoconfiguration

### Fixed
- The stressTest endpoint has been fixed to work with transform flows.
- Fixed migration issue where`includeNormalizeFlows` and `excludeNormalizeFlows` were not getting set in existing egress flows and plans

## [1.1.4] - 2023-09-20

### Added
- Added a scheduled task to periodically attempt to revalidate any invalid flows

### Changed
- Added human-readable bytes to Ingress Status Check output and event.
- Plugin registration will now trigger revalidation of any invalid flows

### Fixed
- Added positive check to Content Storage metrics method.

## [1.1.3] - 2023-09-19

### Added
- Add JoltTransformAction for JSON transformations using the Jolt library

### Changed
- Auto Resume badge tooltip now shows relative time.

### Fixed
- Issue keeping clickhouse from initializing correctly
- Issue with metrics dashboard refreshing variables on time range changes

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
- Add schema version to deltaFiles query to prevent spurious up conversions
- Fix possible NPEs in schema up conversion

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
- New user role `ResumePolicyApply` in the `Resume Policies` group grants permission to execute the `applyResumePolicies` mutation

### Changed
- Clarified documentation that the `flow` in an auto resume policy refers to the DeltaFile's sourceInfo flow. I.e., the ingress or transformation flow name

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
- Allow Transform and Load Actions to create annotations (formerly known as indexed metadata)
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
- Remove unnecessary ProtocolLayer from load and transform response wire protocols
- Remove sourceInfo from the wire protocol
- Update versions of Python package dependencies

### Documentation
- Update 'Getting Started' tutorial to reflect recent changes

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

[Unreleased]: https://gitlab.com/deltafi/deltafi/-/compare/2.27.0...main
[2.27.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.26.1...2.27.0
[2.26.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.26.0...2.26.1
[2.26.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.25.4...2.26.0
[2.25.4]: https://gitlab.com/deltafi/deltafi/-/compare/2.25.3...2.25.4
[2.25.3]: https://gitlab.com/deltafi/deltafi/-/compare/2.25.2...2.25.3
[2.25.2]: https://gitlab.com/deltafi/deltafi/-/compare/2.25.1...2.25.2
[2.25.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.25.0...2.25.1
[2.25.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.24.0...2.25.0
[2.24.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.23.0...2.24.0
[2.23.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.22.1...2.23.0
[2.22.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.22.0...2.22.1
[2.22.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.21.0...2.22.0
[2.21.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.20.0...2.21.0
[2.20.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.19.0...2.20.0
[2.19.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.18.0...2.19.0
[2.18.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.17.0...2.18.0
[2.17.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.16.0...2.17.0
[2.16.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.15.1...2.16.0
[2.15.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.15.0...2.15.1
[2.15.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.14.0...2.15.0
[2.14.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.13.0...2.14.0
[2.13.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.12.0...2.13.0
[2.12.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.11.0...2.12.0
[2.11.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.10.2...2.11.0
[2.10.2]: https://gitlab.com/deltafi/deltafi/-/compare/2.10.1...2.10.2
[2.10.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.10.0...2.10.1
[2.10.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.9.0...2.10.0
[2.9.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.8.0...2.9.0
[2.8.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.7.1...2.8.0
[2.7.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.7.0...2.7.1
[2.7.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.6.1...2.7.0
[2.6.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.6.0...2.6.1
[2.6.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.5.0...2.6.0
[2.5.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.4.0...2.5.0
[2.4.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.3.1...2.4.0
[2.3.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.3.0...2.3.1
[2.3.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.2.1...2.3.0
[2.2.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.2.0...2.2.1
[2.2.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.1.1...2.2.0
[2.1.1]: https://gitlab.com/deltafi/deltafi/-/compare/2.1.0...2.1.1
[2.1.0]: https://gitlab.com/deltafi/deltafi/-/compare/2.0.0...2.1.0
[2.0.0]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.17...2.0.0
[1.2.17]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.16...1.2.17
[1.2.16]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.15...1.2.16
[1.2.15]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.14...1.2.15
[1.2.14]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.13...1.2.14
[1.2.13]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.12...1.2.13
[1.2.12]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.11...1.2.12
[1.2.11]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.10...1.2.11
[1.2.10]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.9...1.2.10
[1.2.9]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.8...1.2.9
[1.2.8]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.7...1.2.8
[1.2.7]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.6...1.2.7
[1.2.6]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.5...1.2.6
[1.2.5]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.4...1.2.5
[1.2.4]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.2...1.2.4
[1.2.2]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.1...1.2.2
[1.2.1]: https://gitlab.com/deltafi/deltafi/-/compare/1.2.0...1.2.1
[1.2.0]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.20...1.2.0
[1.1.20]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.18...1.1.20
[1.1.18]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.17...1.1.18
[1.1.17]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.16...1.1.17
[1.1.16]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.15...1.1.16
[1.1.15]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.14...1.1.15
[1.1.14]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.13...1.1.14
[1.1.13]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.12...1.1.13
[1.1.12]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.11...1.1.12
[1.1.11]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.10...1.1.11
[1.1.10]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.9...1.1.10
[1.1.9]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.8...1.1.9
[1.1.8]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.7...1.1.8
[1.1.7]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.6...1.1.7
[1.1.6]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.5...1.1.6
[1.1.5]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.4...1.1.5
[1.1.4]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.3...1.1.4
[1.1.3]: https://gitlab.com/deltafi/deltafi/-/compare/1.1.2...1.1.3
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
