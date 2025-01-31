# Changes on branch `templated-action-params`
Document any changes on this branch here.
### Added
- Added the option to use templates in action parameters that pull information from the ActionInput that is sent to the action.  The templates support the Spring Expression Language (SpEL), i.e. you can use things like `{{ deltaFileName.toUpperCase() }}`. The following top level fields are available to use in the templates:
    - {{ deltaFileName }} - the deltaFileName of the DeltaFile 
    - {{ did }} - the did of the DeltaFile
    - {{ metadata }} - the metadata from the first DeltaFileMessage
    - {{ content }} - the content list from the first DeltaFileMessage
    - {{ actionContext }} - the actionContext from the ActionInput being sent to the action
    - {{ deltaFileMessages }} - the full list of DeltaFileMessages, useful for joins
    - {{ now() }} - helper method to get the current timestamp

### Changed
- The `Merge` transform action no longer supports the {{filename}} placeholder in the filename parameter, it is replaced by the common parameter templating

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Existing flows that use the `Merge` action need to be updated to use the new templating instead of {{filename}}
- Any action parameters that contain '{{ }}' will be treated as templates. If the template contains text that cannot be mapped to a Spring Expression, the parameter will be considered invalid resulting in an error
