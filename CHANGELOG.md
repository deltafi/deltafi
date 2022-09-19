# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased] - Next release 0.98.1

### Added
- Added optional documentation server deployment enabled via values.yaml

### Changed
- Increased action queue check threshold
- Decreased action registration period
- Improved default delete policy names and configuration
- Egress does not generate smoke when artificial-enrichment is disabled

### Deprecated

### Removed
- Removed deprecated type, produces, and consumes fields

### Fixed

### Tech-Debt/Refactor

### Security

### Upgrade and Migration

## [Unreleased] - Next release 0.98.0

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

[Unreleased]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.98.0...main
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

