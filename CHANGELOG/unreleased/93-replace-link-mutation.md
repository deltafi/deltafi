# Changes on branch `93-replace-link-mutation`
Document any changes on this branch here.
### Added
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

### Changed
- 

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
- 
