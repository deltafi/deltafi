# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Added
- KinD: Detect arm/Apple Silicon and load overrides for arm64 compatible Bitnami images
- KinD: `deltafi` CLI wrapper that allows CLI to be used natively with KinD
- KinD: `cluster` command output cleaned up and streamlined
- Pod memory and CPU metrics enabled in the kubernetes dashboard

### Changed
- `deltafi` CLI checks for required tool installation on execution
- Nodemonitor logging cleaned up, converted to JSON

### Deprecated

### Removed

### Fixed
- Parameter schemas will now properly validate with list and map variable values
- DeltaFi CLI checks for dependencies (`jq`, `kubectl`, etc.)
- Nodemonitor fix for long filesystem names
- Null/empty array issue with includeIngressFlows
- Warnings on Quarkus app startup resolved
- KinD: Fix for starting cluster with a redundant config map

### Tech-Debt/Refactor

### Security

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

[Unreleased]: https://gitlab.com/systolic/deltafi/deltafi/-/compare/0.96.1...main
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
