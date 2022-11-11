# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.99.3]

### Changed

- Search criteria is now serialized to/from GET vars (#273)
- Added loading screen on app startup (#207)
- Secured UI with Permissions (#278)
- System version now displayed in header (#296)

### Fixed

- Fixed bug in notification service (#292)
- Fixed toggle switch bug on Flows page (#299)

## [0.99.2]

### Changed

- Added `uploadedBy` field to metadata of all uploaded DeltaFiles (#283)
- Added support for content segments (#288)
- Added Test Mode for Flows. (#285)

### Fixed

- Fixed broken Indexed Metadata links to Search page (#284)
- Fixed blank error message Toast (#287)
- Fixed issue with error resume on inactive flows (#290)

## [0.99.1]

### Changed

- Batch resume messages now show up links for up to 10 DIDs (#281)
- Added row highlight to tables with an action column (#279)

## [0.99.0]

### Changed

- Filename filter on Search Page is now plain text and supports partial search (#266)
- Integrated default external links into core menus (#269)
- Added support for displaying Indexed Metadata on DeltaFile Viewer page (#252)
- Added New Dashboard Panels (#265)
- Added Import and Rollback capabilities to System Snapshots page (#259)
- Page size on Search and Errors pages is now persisted (#272)
- Added link to internal documentation (#280)

### Fixed

- Fixed bug where incorrect indexed metadata keys would be shown in search dropdown (#270)
- Fixed inaccurate Progress Bar on batch error resume, acknowledged, and getting all metadata (#268)
- Fixed Filename search option to trim provided values (#271)
- Fixed issue with perPage variable on Search Page keeping search options panel always open (#276)
- Fixed issue with Batching showing multiple toasts. (#275)

## [0.98.5]

### Changed

- Added support for using DeltaFile fields in deltaFileLinks URL templates (#229)
- Updated Raw DeltaFile view to use new rawDeltaFile GraphQL endpoint (#261)
- Added link to corresponding Grafana logs on Action Metrics page (#236)
- Duplicate Toast messages no longer stack (#237)
- Added support for HTML in Toast messages (#263)
- Toast messages from successful Replays now link to DeltaFile Viewer (#264)

### Fixed

- Sidebar now has a scrollbar to see all menu options (#217)

## [0.98.4]

### Changes

- Added System Snapshots page (#234)

### Fixed

- Fixed bug causing `Filtered` and `Egressed` fields to not persist properly on Search page. (#260)

## [0.98.3]

### Changed

- DialogTemplate components now use standard PrimeVue footers (#248)
- Remove Ingress Flow requirement from Upload Page (#254)
- Added import/export Metadata to/from Upload page (#190)
- Refactored file upload buttons on Delete Policies and Ingress Routing (#239)

### Fixed

- Fixed bug with batching when resuming < 500 errors (#254)
- Fixed bug with selecting on By Message error summary (#257)
- Fixed mocks used in Deltafile Viewer (#258)
- Fixed bug hiding horizontal scrollbar on Metadata viewer (#256)
- Fixed bug where list/map plugin variable defaults were displayed as strings (#228)

## [0.98.2]

### Fixed

- Removed `type` field from top level `getIngressFlow` graphql query to fix error (#253)

## [0.98.1]

### Changed

- Added ingressBytes to raw DeltaFile view (#249)
- Renamed fields on DeltaFile viewer page (#250)
  - `Original Filename` -> `Filename`
  - `Original File Size` -> `Ingress Size`
  - `Total File Size` -> `Total Size`
- Updated Search Page
  - Allowed filtering on Domain (#211)
  - Allowed filtering on Indexed Metadata (#211)
  - Allowed filtering on Ingress Bytes (#235)
  - Allowed filtering on Total Bytes (#251)
  - Removed Action Type filter (#247)
  - Query optimization (#231)

## [0.98.0]

### Changed

- Added Errors summary views (#202)
- DID links now display only the first 8 digits of the DID. (#215)
- Updated deletePolicy code and Schema to work with Id an Name fields (#224)
- Updated enrich flows to include DomainActions (#218)
- Moved all used Mocks to TypeScript (#213)
- Mocks now reload data on each call (#222)
- Added Error Search Options (#230)
- Added Ingress Routing page (#197)
- Errors page now uses new `resume` GraphQL mutation (#240)

### Fixed

- Fixed bug with Parent/Child DeltaFiles not refreshing on refresh/replay (#214)
- Fixed bug related to adding a new timeDeletePolicy (#223)
- Fixed bug preventing progress bars from being displayed on Upload page (#226)
- Fixed bug preventing Error selection clear after resume/replay (#227)
- Fixed bug related to flow toggle confirmation dialog not sticking to button (#238)
- Resuming Errors is now batched (#232)
- Acknowledging Errors is now batched (#233)
- Ingress Routing allows both filenameRegex and requiredMetadata fields to be uploaded/updated (#241)

## [0.97.0]

### Changed

- Added indexed metadata to raw DeltaFile view (#208)
- Limited ingress flow selection on Upload Files page to running ingress flows (#204)
- Reworked plugins page to make editing variables safer and more user-friendly (#203)
- Added Delete Policy page (#194)

## [0.96.4]

### Changed

- Added live metrics to Flow Viewer bit rate to Flows page (#196)
- Flow Page update (#198)
- Removed unnecessary code related to bug #205
- Added current user badge in top right corner (#206)

## [0.96.3]

### Fixed

- Bug where "Show Acknowledged" button text would wrap in Firefox (#205)
- Bug where dates on Users page would display "Invalid Date" in Firefox (#205)

## [0.96.2]

### Changed

- Renamed Retry to Resume (#193)
- Added support for Resuming Errors with metadata updates (#186)
- Added support for Replaying DeltaFiles with metadata updates (#195)

### Fixed

- Bug where trace chart action names would sometimes clash with time labels (#201)

## [0.96.1]

### Fixed

- Bug where `Total File Size` field on DeltaFile viewer was showing wrong value (#199)

## [0.96.0]

### Changed

- Added support for Basic Authentication on Users page (#191)
- Changes to support Config Server / Core Domain consolidation (#187)
- Added refresh button to DeltaFile Viewer page (#183)
- Added Flow Viewer (#157)
- Added warning message when viewing a DeltaFile without content (#192)
- Added confirmation prompt when disabling flows (#188)

### Fixed

- Bug where trace chart on child DeltaFile with no actions shows actions of parent (#184)

## [0.95.4]

### Changed

- Added GraphiQL UI (#181)
- Added User Management page (#182)

## [0.95.3]

### Changed

- Added Pretty Print to Content Viewer for JSON and XML (#84)
- Added auto select on right click for errors table (#161)
- Added enrich flows to the flows page (#171)
- Added message on flow pages if no flow is found (#176)
- Changed Divider color on flows page (#177)
- Made content dialogs not draggable (#175)
- Moved Flows config to a dialog box on Flows page (#168)
- Moved Queue Metrics to Action Metrics page (#180)
- Removed link to Zipkin from DeltaFile Viewer page (#185)
- Updated Trace chart to use queued vs created and added start/stop bar (#166)

## [0.95.2]

### Changed

- Fixed flow validation query bug (#173)

## [0.95.1]

### Changed

- Added Apache 2 license header to all source files (#167)
- Added Flows page (#119)
- Added Plugins page (#120)
- Added Total File Size to DeltaFile Viewer page (#164)
- Added bytes tooltip on size in content viewer (#158)
- Added sortable Size column to DeltaFile Search page (#164)
- Removed keys with null values from GraphQL queries (#150)
- Trace chart now handles retries better (#140)
- Added Egressed and Filtered search options to Search Page (#165)
- Fixed encoding bug in content viewer (#170)

## [0.21.2]

### Changed

- Replaced some collapsible Panels with standard Panels (#153)

## [0.21.1]

### Changed

- Versions page now groups versions by Core, Plugins, and Other. (#147)
- Made top bar color configurable. (#152)
- Moved Content Viewer overlay menu to panel header. (#151)
- Moved paging to panel header on Search page and Errors page. (#149)
- Made improvements to the Queue Metrics page. (#148)
  - Updated column widths
  - Added relative timestamp tooltips
  - Added row hover

### Fixed

- Margin bug in content viewer. (#146)
- Properly handle unknown file types on upload. (#156)

## [0.21.0]

### Added

- Display Content-level MetaData in Content Dialog (#132)
- Added Copy to Clipboard button to Content Viewer (#144)
- Loading indicator for Parent/Child DeltaFiles (#142)
- Paging for Parent/Child DeltaFiles (#143)

### Changed

- Max page size to 1000 on Search and Errors pages (#139)

## [0.20.0]

### Fixed

- Bug preventing child DeltaFiles from being displayed when no parent DeltaFiles are present. (#134)
- Bug that was mutating DeltaFile action metadata. (#135)
- Bug improperly setting the MIME type of uploaded files to `application/x-www-form-urlencoded`. (#137)
- Bug causing the Content Dialog to crash when clicking selected item. (#138)

## [0.19.1]

### Added

- Ability to repopulate metadata on DeltaFi Upload page. (#125)
- Ability to see Parent and Child DeltaFiles on DeltaFile Viewer Page (#121)

### Changed

- Search, Error, and Upload pages now only show ingress flows. (#129)
- Periodic background API calls are now paused when page is idle. (#118)
- Uploaded files are now sorted by time descending on Upload page. (#128)
- Trace chart colors for actions in the ERROR, RETRIED, FILTERED state now match the Actions table. (#127)

### Fixed

- Periodic background API calls now wait for previous call to finish. (#130)

## [0.19.0]

### Added

- Clock to top right. (#112)
- Ability to mock service calls. (#90)
- More options to Action Metrics timeframe dropdown. (#114)
- Flow and word filters to Action Metrics page (#80)
- Support for multiple GraphQL services. (#115)
- Trace chart to Viewer page. (#45)

### Changed

- Better handling of long filenames throughout the UI. (#109)
- The refresh button now changes color when viewing stale data on the Errors page. (#105)
  - Stale data Toast message was also removed.
- When retrying errors on the Errors page, the table is now refreshed. (#108)
- Normalized timestamps throughout the app. (#112)
- Search page timestamps now match the selected timezone. (#111)
- Upload results and metadata are now preserved between router page loads (#117)

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

[unreleased]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.99.3...main
[0.99.3]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.99.2...0.99.3
[0.99.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.99.1...0.99.2
[0.99.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.99.0...0.99.1
[0.99.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.5...0.99.0
[0.98.5]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.4...0.98.5
[0.98.4]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.3...0.98.4
[0.98.3]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.2...0.98.3
[0.98.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.1...0.98.2
[0.98.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.98.0...0.98.1
[0.98.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.97.0...0.98.0
[0.97.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.96.4...0.97.0
[0.96.4]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.96.3...0.96.4
[0.96.3]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.96.2...0.96.3
[0.96.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.96.1...0.96.2
[0.96.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.96.0...0.96.1
[0.96.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.95.4...0.96.0
[0.95.4]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.95.3...0.95.4
[0.95.3]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.95.2...0.95.3
[0.95.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.95.1...0.95.2
[0.95.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.21.2...0.95.1
[0.21.2]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.21.1...0.21.2
[0.21.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.21.0...0.21.1
[0.21.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.20.0...0.21.0
[0.20.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.19.1...0.20.0
[0.19.1]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.19.0...0.19.1
[0.19.0]: https://gitlab.com/systolic/deltafi/deltafi-ui/-/compare/0.18.1...0.19.0
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
