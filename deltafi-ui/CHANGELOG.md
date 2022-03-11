# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- Clock to top right. (#112)
- Ability to mock service calls. (#90)
- Expand Action Metrics timeframe dropdown. (#114)
- Support for multiple GraphQL services. (#115)
- Action Metrics have flow and word filter. (#80)
- Preserve upload results between router page loads (#117)

### Changed
- Better handling of long filenames throughout the UI. (#109)
- When viewing stale data on the Errors page, the refresh button now changes color. (#105)
  - Stale data Toast message also removed.
- When retrying errors on the Errors page, the table is now refreshed. (#108)
- Normalized timestamps throughout the app. (#112)
- Search page timestamps now match the selected timezone. (#111)

## [0.18.1]

### Added
- Server-Sent Events

### Changed
- Metadata viewer table is now sortable
- Allow `FILTERED` actions to be inspected in DeltaFile Viewer

### Fixed
- Bug on System Properties page causing name and description of PropertySets not to be displayed
- Bug preventing viewing all metadata in DeltaFile Viewer

## [0.18.0]

### Changed
- Composition API Refactor

## [0.17.0] - 2022-02-04

### Changed
- Errors can now be acknowledged
- Added Show/Hide Acknowledged button to Errors page
- Date range selected has been removed from Errors page
- DeltaFile Viewer now displays Domain and Enrichment data
- DeltaFile Viewer now displays metadata for all Actions that include metadata
- DeltaFile now allows for support of smaller screen sizes
- Default date range for Search page now start and end of current day

## [0.16.3] - 2022-01-19
### Added
- Persisted Search Options on DeltaFile Search page
- Ability to view content produced by Actions on DeltaFile Viewer page
- Configurable Security Banner

### Changed
- Changed the way errored actions are displayed on the Errors page
- Dropdown menus on all pages are now sorted alphabetically

### Fixed
- Action sorting bug on DeltaFile Viewer page

## [0.16.2] - 2022-01-13
### Fixed
- Bug on Upload page causing errors to be swallowed

## [0.16.1] - 2022-01-04
### Changed
- Errors page now supports multi-select for Retry
- CSS standardization

## [0.16.0] - 2022-01-04
### Added
- Loading indicator to DeltaFile Viewer
- Flow Configuation page
- JSON highlighting to Raw DeltaFile view
- DeltaFile Upload page

### Changed
- GraphQL queries to support new Content Service

## [0.15.0] - 2021-12-20
### Added
- Action Metrics page
- Queue Metrics page
- Versions page
- Paging on Search and Errors pages
- New menu button to DeltaFile Viewer page
- Direct link to Zipkin from DeltaFile Viewer page

### Changed
- Updated `sass-loader` library
- DID column on Search page now uses monospace font
- Toast messages now appear in bottom right
- Replaced Vue.js favicon
- Replaced PrimeIcons with FontAwesome
- Error dialog will now pretty print JSON
- External Links now appear in the menu and are populated by the API
- All Panels updated to new CollapsiblePanel component

### Fixed
- App scaling issue

## [0.14.1] - 2021-12-03
### Added
- DeltaFile Viewer page
- DeltaFile Search page
- System Properties page

## [0.13.0] - 2021-11-17
### Added
- Charts to System Metrics page
- Kubernetes namespace to pod table on System Metrics page
- Flow filter to Errors page

## Changed
- App name/title now configurable
- Updated menu to be collapsible

### Removed
- NiFi link from Dashboard
- Requested resource information from System Metrics

[Unreleased]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.18.1...main
[0.18.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.18.0...0.18.1
[0.18.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.17.0...0.18.0
[0.17.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.16.3...0.17.0
[0.16.3]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.16.2...0.16.3
[0.16.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.16.1...0.16.2
[0.16.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.16.0...0.16.1
[0.16.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.15.0...0.16.0
[0.15.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.14.1...0.15.0
[0.14.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.13.0...0.14.1
[0.13.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.12.0...0.13.0
