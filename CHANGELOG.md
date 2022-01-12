# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]
### Added
- Load and save content as strings in the ContentService
- Added plugin registry
- (API) Added content endpoint

### Changed
- No restart for RETRIED terminal actions
- IngressAction added to DeltaFile actions list
- Renamed Action schema classes
### Deprecated

### Removed

### Fixed
- Add Ingress Action to protocol stack
- EgressFlowConfigurationInput no longer missing EgressAction
- Intermittent bug with git version in gradle (mainly CI affecting)

### Tech-Debt/Refactor

### Security

## [0.16.1] 2022-01-04

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
