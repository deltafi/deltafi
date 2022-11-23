# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased] - Next release 0.99.5

### Added
- Audit entries for deleted dids
- Added new `ingressFlows` field to SourceInfo for DeltaFiles filter

### Changed

### Deprecated
- Deprecated `flow` field in DeltaFiles SourceInfo filtering; use 'ingressFlows' instead.  `flow` will still work at the moment, but will be removed in a future release.

### Removed

### Fixed
- Issue where syncing properties was thrashing between multiple instances of `deltafi-core`

### Tech-Debt/Refactor

### Security

### Upgrade and Migration

## [0.99.4] - 2022-11-22

### Added
- New query `getFlowNames` that returns a list of flow names grouped by flow type and can be filtered by flow state
- Grafana alerts will be displayed in the main UI monitor list and cause a degraded state
- Python action development kit
- Enhanced test framework for plugins

### Changed
- Add fsGroupChangePolicy: OnRootMismatch to (dramatically) speed up minio pod startup
- Metrics are now generated from the core for all actions.  Custom metrics are reported to the core via an addition to the ActionEventInput schema.

### Removed
- `files_completed` metric was removed.  It was redundant with other metric information and not used in dashboards.

### Fixed
- Issue in audit logger causing username to be tagged to logs erroneously
- Issue with delete action performance
- Issue with delete action and requeue scheduling

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
- Consolidate Action input parameters: each action now takes a context, parameters, and a single action input (e.g. LoadInput, ValidateInput, etc) that contains the other members specific to that action
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
return a `507` error when there is not enough free content storage.
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
`bootJar.dependsOn(checkDeltafiPlugin)`.
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
constructor.
- CLI commands `install-plugin` and `uninstall-plugin` now take `groupId:artifactid:version` of the plugin
- The `uninstallPlugin` mutation no longer takes a dry-run flag
- Move delete policy "Preparing to execute" log messages to DEBUG level
- Make action name an optional field on transform and load completion, and have the core fill it in (don't trust the action to fill it)
- Disk delete policy only deletes files that have completed processing or have been cancelled

### Removed
- Plugin manifest generation has been removed from the DeltaFi Gradle plugin.

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
- Bug in Graphite does not gracefully allow for null tags.  Removed Dropped Files metric from the report, since it may have null ingressFlow tags.
- Improved initialization time for deltafi-core

### Tech-Debt/Refactor
- Do not store 0-byte files in minio
- Use Kubernetes node name in nodemonitor metrics
- Merge ingress code into core. Start ingress as a separate core instance with most core services disabled. Remove the overhead of an internal HTTP call on ingress
- Plugins now register themselves with their actions, variables, and flows on startup
- Unused Spring Boot services disabled by configuration in core
- Restrict Result types for each Action type.

### Upgrade and Migration
- Upgrade Grafana to 9.1.7
- Upgrade Grafana helm chart to 6.40.3.  Air-gapped installs will need this new chart.
- Base docker image updated to `deltafi/deltafi-spring-base:0.99.0`
- KinD: Node image updated to `deltafi/deltafi-kind-node:0.99.0`
- Graphite chart is now a local chart versioned as 0.99.0.  Air-gapped installs will need this new chart.
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
- Plugin flow files now require a type field. Valid values are INGRESS, ENRICH, and EGRESS.
- Plugin variables.json files need to have the extra "variables" field removed, making it just an array of variables.

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
- DecompressionTransformAction will log error results.
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
- Added auto configuration classes to deltafi-common and deltafi-actionkit. It's no longer necessary to specify base packages to scan.
- Removed `@Configuration` from `@ConfigurationProperties` classes. The @ConfigurationPropertiesScan in the auto configurations doesn't need it.
- Migrated Java StatsD client from UDP to TCP to guarantee metric delivery and handle metrics back-up when graphite is down
- Changed name of deltafi-core-domain to deltafi-core
- Disabled spring log banners in deltafi-ingress and deltafi-core-actions
- MinIO storage creation now done in deltafi-core instead of helm
- Flow assignment rules now has a name that is independent of the rule ID
- Removed "egressFlow" parameter for EgressActions.
- Changed "minio.expiration-days" property to be editable and refreshable

### Deprecated
- Quarkus is no longer in use or supported in the DeltaFi monolith

### Removed
- Liveness probes removed from deltafi-core-actions, since it is no longer a web application and exposes no monitoring endpoints

### Fixed
- Resolution loss and dropped initial metrics issues are resolved in dashboards
- Bitrate gauges on dashboards no longer flatline periodically
- Metric summaries are now accruate at large time scales
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
- Remove minio housekeeping routine due to scaling issues. Depend on minio ttl instead.

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
- Removed core domain dependencies from action kit.  This is a breaking change for plugins.
- KinD updated to have a simpler initial user experience

### Upgrade and Migration
- Refactored common and core domain to remove deltafi-core-domain dependencies will require refactoring of all plugins to move to the new common dependencies.
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
- Common metrics API is deprectated.  A new micrometer API is forthcoming backed by Graphite and Grafana

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

No changes.  Supporting UI release.

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

### Tech-Debt/Refactor
- DRY'ed up gradle build files

### Security
- Forced all projects to log4j 2.17.0 to avoid CVEs

[Unreleased]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.99.4...main
[0.99.4]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.99.3...0.99.4
[0.99.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.99.2...0.99.3
[0.99.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.99.1...0.99.2
[0.99.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.99.0...0.99.1
[0.99.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.5...0.99.0
[0.98.5]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.4...0.98.5
[0.98.4]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.3...0.98.4
[0.98.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.2...0.98.3
[0.98.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.1...0.98.2
[0.98.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.0...0.98.1
[0.98.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.97.0...0.98.0
[0.97.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.4...0.97.0
[0.96.4]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.3...0.96.4
[0.96.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.2...0.96.3
[0.96.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.1...0.96.2
[0.96.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.0...0.96.1
[0.96.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.95.4...0.96.0
[0.95.4]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.95.3...0.95.4
[0.95.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.95.2...0.95.3
[0.95.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.21.4...0.95.2
[0.21.4]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.21.3...0.21.4
[0.21.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.21.2...0.21.3
[0.21.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.21.1...0.21.2
[0.21.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.21.0...0.21.1
[0.21.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.20.0...0.21.0
[0.20.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.19.1...0.20.0
[0.19.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.19.0...0.19.1
[0.19.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.18.2...0.19.0
[0.18.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.18.1...0.18.2
[0.18.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.18.0...0.18.1
[0.18.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.17.0...0.18.0
[0.17.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.16.3...0.17.0
[0.16.3]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.16.2...0.16.3
[0.16.2]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.16.1...0.16.2
[0.16.1]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.16.0...0.16.1
[0.16.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.15.0...0.16.0
[0.15.0]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.14.1...0.15.0
